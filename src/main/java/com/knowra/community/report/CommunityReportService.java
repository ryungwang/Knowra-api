package com.knowra.community.report;

import com.knowra.community.modlog.ModLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 커뮤니티 운영진 신고함 — 자기 커뮤니티의 글·댓글 신고를 보고 처리한다.
 *
 * <p>같은 글에 들어온 신고는 한 줄로 묶는다(몇 명이 무슨 사유로). 신고한 사람은 보여 주지 않는다 — 신고는 익명으로
 * 받는다고 약속했다. 익명 커뮤니티에선 작성자도 보여 주지 않는다(운영진이라도 글쓴이를 알 수 없게 한 곳이다).
 * 운영진 처리는 COMM_STAT 만 바꾼다 — 사이트 관리자 큐(STAT)는 그대로 남는다.
 */
@Service
@RequiredArgsConstructor
public class CommunityReportService {

    private final JdbcTemplate jdbc;
    private final ModLogService modLog;
    private final com.knowra.safety.service.ReportAutoHide autoHide;

    public long openCount(long commSn) {
        Long n = jdbc.queryForObject("SELECT COUNT(DISTINCT (target_kind, target_sn)) FROM knowra_user.tbl_report WHERE comm_sn = ? AND comm_stat = 'OPEN'", Long.class, commSn);
        return n == null ? 0 : n;
    }

    /** stat: OPEN(대기) | DONE(처리·기각) */
    public List<Map<String, Object>> list(long commSn, String stat, long me) {
        requireManager(commSn, me);
        boolean anonymous = "anonymous".equals(jdbc.query("SELECT prvcy_stng FROM knowra_community.tbl_comm WHERE comm_sn = ?",
                rs -> rs.next() ? rs.getString(1) : null, commSn));
        String where = "DONE".equals(stat) ? "comm_stat IN ('RESOLVED','DISMISSED')" : "comm_stat = 'OPEN'";
        List<Map<String, Object>> groups = jdbc.queryForList("""
                SELECT target_kind, target_sn, COUNT(*) AS cnt, MAX(report_sn) AS last_sn, MAX(frst_crt_dt) AS last_at,
                       string_agg(DISTINCT reason, ',') AS reasons,
                       string_agg(detail, '\u001f' ORDER BY report_sn) FILTER (WHERE detail IS NOT NULL AND detail <> '') AS details,
                       MAX(comm_stat) AS comm_stat, MAX(comm_handled_at) AS handled_at
                FROM knowra_user.tbl_report WHERE comm_sn = ? AND""" + " " + where + """
                 GROUP BY target_kind, target_sn ORDER BY MAX(report_sn) DESC LIMIT 50""", commSn);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> g : groups) {
            String kind = (String) g.get("target_kind");
            long sn = ((Number) g.get("target_sn")).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("targetKind", kind); m.put("targetSn", sn);
            m.put("count", g.get("cnt")); m.put("lastAt", g.get("last_at"));
            m.put("reasons", g.get("reasons") == null ? List.of() : List.of(((String) g.get("reasons")).split(",")));
            m.put("details", g.get("details") == null ? List.of() : List.of(((String) g.get("details")).split("\u001f")));
            m.put("stat", g.get("comm_stat")); m.put("handledAt", g.get("handled_at"));
            Map<String, Object> t = "POST".equals(kind)
                    ? first(jdbc.queryForList("""
                        SELECT p.post_sn AS "postSn", p.post_ttl AS title, p.post_cntnt AS body, p.actvtn_yn = 'Y' AND p.stat IN ('ACTIVE','REVIEW') AS alive, p.stat = 'REVIEW' AS review,
                               u.user_sn AS "userSn", u.nick_name AS "nickName", u.name AS name
                        FROM knowra_post.tbl_post p JOIN knowra_user.tbl_user u ON u.user_sn = p.user_sn WHERE p.post_sn = ?""", sn))
                    : first(jdbc.queryForList("""
                        SELECT c.post_sn AS "postSn", c.post_cmt_sn AS "postCmtSn", c.cmt_cntnt AS body, c.actvtn_yn = 'Y' AND c.stat IN ('ACTIVE','REVIEW') AS alive, c.stat = 'REVIEW' AS review,
                               u.user_sn AS "userSn", u.nick_name AS "nickName", u.name AS name
                        FROM knowra_post.tbl_post_cmt c JOIN knowra_user.tbl_user u ON u.user_sn = c.user_sn WHERE c.post_cmt_sn = ?""", sn));
            if (t == null) { m.put("gone", true); out.add(m); continue; }
            m.put("gone", !Boolean.TRUE.equals(t.get("alive")));
            m.put("autoHidden", Boolean.TRUE.equals(t.get("review")));
            m.put("postSn", t.get("postSn"));
            if (t.get("postCmtSn") != null) m.put("postCmtSn", t.get("postCmtSn"));
            if (t.get("title") != null) m.put("title", t.get("title"));
            m.put("preview", snippet((String) t.get("body")));
            if (!anonymous) {
                // 그 커뮤니티에서의 역할 — 화면이 「내보내기」를 일반 멤버에게만 단다(주인·운영진은 서버도 못 내보낸다)
                String role = jdbc.query("SELECT role FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE'",
                        rs -> rs.next() ? rs.getString(1) : null, commSn, ((Number) t.get("userSn")).longValue());
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("userSn", t.get("userSn")); a.put("nickName", t.get("nickName")); a.put("name", t.get("name") == null ? "" : t.get("name"));
                a.put("role", role);   // null = 이미 나간 사람
                m.put("author", a);
            }
            out.add(m);
        }
        return out;
    }

    /** action: RESOLVE(조치함) | DISMISS(문제없음) — 그 대상의 대기 신고를 한꺼번에 */
    @Transactional
    public int handle(long commSn, String kind, long targetSn, String action, long me) {
        requireManager(commSn, me);
        if (!Set.of("POST", "CMT").contains(kind)) throw new IllegalArgumentException("지원하지 않는 신고예요.");
        if (!Set.of("RESOLVE", "DISMISS").contains(action)) throw new IllegalArgumentException("지원하지 않는 처리예요.");
        String stat = "RESOLVE".equals(action) ? "RESOLVED" : "DISMISSED";
        int n = jdbc.update("""
                UPDATE knowra_user.tbl_report SET comm_stat = ?, comm_handled_by = ?, comm_handled_at = ?
                WHERE comm_sn = ? AND target_kind = ? AND target_sn = ? AND comm_stat = 'OPEN'""",
                stat, me, LocalDateTime.now(), commSn, kind, targetSn);
        // 문제없음이면 신고 누적으로 가려 둔 것을 되돌린다(운영진은 이 커뮤니티 글을 원래 고치고 지울 수 있다)
        if ("DISMISS".equals(action)) autoHide.restore(kind, targetSn);
        if (n > 0) {
            Long postSn = "POST".equals(kind) ? Long.valueOf(targetSn)
                    : jdbc.query("SELECT post_sn FROM knowra_post.tbl_post_cmt WHERE post_cmt_sn = ?", rs -> rs.next() ? rs.getLong(1) : null, targetSn);
            modLog.record(commSn, me, "RESOLVE".equals(action) ? ModLogService.REPORT_RESOLVE : ModLogService.REPORT_DISMISS, null, postSn,
                    com.knowra.community.modlog.ModLogDetail.reports(kind, n));
        }
        return n;
    }

    private void requireManager(long commSn, long me) {
        String role = jdbc.query("SELECT role FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE'",
                rs -> rs.next() ? rs.getString(1) : null, commSn, me);
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) throw new IllegalArgumentException("운영진만 볼 수 있어요.");
    }

    private static Map<String, Object> first(List<Map<String, Object>> rows) { return rows.isEmpty() ? null : rows.get(0); }

    private static String snippet(String s) {
        if (s == null) return "";
        String plain = org.jsoup.Jsoup.parse(s).text();
        return plain.length() > 160 ? plain.substring(0, 160) + "…" : plain;
    }
}
