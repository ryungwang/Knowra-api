package com.knowra.community.hold;

import com.knowra.cmm.event.NotifTriggerEvent;
import com.knowra.community.modlog.ModLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 새 멤버 글 검토 — 커뮤니티가 정한 날수 안에 들어온 멤버의 새 글을 HELD 로 둔다. 운영진이 승인하면 올라간다.
 *
 * <p>운영진(OWNER·ADMIN)의 글은 잡지 않는다. 날수는 0(끔)~30.
 * 올리기(승인)는 글 쪽 일(피드·알림·뱃지)이라 {@link com.knowra.post.service.PostService#releaseHeld} 가 마저 한다.
 */
@Service
@RequiredArgsConstructor
public class HoldService {

    public static final String HELD = "HELD";
    public static final Set<Integer> DAYS = Set.of(0, 1, 3, 7, 14, 30);

    private final JdbcTemplate jdbc;
    private final ModLogService modLog;
    private final ApplicationEventPublisher events;

    /** 이 사람이 지금 이 커뮤니티에 쓰는 글을 잡아 둘까 */
    public boolean needsHold(Long commSn, long userSn) {
        if (commSn == null) return false;
        Integer days = jdbc.query("SELECT new_member_hold_days FROM knowra_community.tbl_comm WHERE comm_sn = ?", rs -> rs.next() ? rs.getInt(1) : 0, commSn);
        if (days == null || days <= 0) return false;
        var row = jdbc.queryForList("SELECT role, joined_at FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE'", commSn, userSn);
        if (row.isEmpty()) return false;
        String role = (String) row.get(0).get("role");
        if ("OWNER".equals(role) || "ADMIN".equals(role)) return false;
        Object joined = row.get(0).get("joined_at");
        return joined == null || ((java.sql.Timestamp) joined).toLocalDateTime().isAfter(LocalDateTime.now().minusDays(days));
    }

    public int days(long commSn) {
        Integer d = jdbc.query("SELECT new_member_hold_days FROM knowra_community.tbl_comm WHERE comm_sn = ?", rs -> rs.next() ? rs.getInt(1) : 0, commSn);
        return d == null ? 0 : d;
    }

    public void setDays(long commSn, long me, int days) {
        requireManager(commSn, me);
        if (!DAYS.contains(days)) throw new IllegalArgumentException("고를 수 있는 날수가 아니에요.");
        if (jdbc.update("UPDATE knowra_community.tbl_comm SET new_member_hold_days = ? WHERE comm_sn = ? AND new_member_hold_days <> ?", days, commSn, days) > 0)
            modLog.record(commSn, me, ModLogService.HOLD_DAYS, null, null, days == 0 ? "끔" : days + "일");
    }

    public long count(long commSn) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_post.tbl_post WHERE comm_sn = ? AND stat = ? AND actvtn_yn = 'Y'", Long.class, commSn, HELD);
        return n == null ? 0 : n;
    }

    /** 검토할 글 — 오래된 것부터. 익명 커뮤니티는 쓴 사람을 내지 않는다 */
    public List<Map<String, Object>> list(long commSn, long me) {
        requireManager(commSn, me);
        boolean anon = "anonymous".equals(jdbc.queryForObject("SELECT prvcy_stng FROM knowra_community.tbl_comm WHERE comm_sn = ?", String.class, commSn));
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT p.post_sn, p.post_ttl, p.post_cntnt, p.frst_crt_dt, u.nick_name, u.name
                FROM knowra_post.tbl_post p JOIN knowra_user.tbl_user u ON u.user_sn = p.user_sn
                WHERE p.comm_sn = ? AND p.stat = ? AND p.actvtn_yn = 'Y' ORDER BY p.post_sn LIMIT 100""", commSn, HELD)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("postSn", r.get("post_sn"));
            m.put("title", r.get("post_ttl"));
            String text = org.jsoup.Jsoup.parse(r.get("post_cntnt") == null ? "" : String.valueOf(r.get("post_cntnt"))).text();
            m.put("excerpt", text.length() > 300 ? text.substring(0, 300) + "…" : text);
            m.put("createdAt", r.get("frst_crt_dt"));
            m.put("nickName", anon ? null : r.get("nick_name"));
            m.put("name", anon ? com.knowra.cmm.i18n.Msg.t("익명") : r.get("name"));
            out.add(m);
        }
        return out;
    }

    /**
     * 승인·거절. 승인이면 HELD → ACTIVE 로 바꾸고 글 번호를 돌려준다(올라간 뒤의 일은 PostService 가).
     * 조건부 갱신이라 운영진 둘이 동시에 눌러도 한 번만 처리된다. 처리 못 했으면(이미 처리됨) null.
     */
    public Long decide(long postSn, long me, boolean approve) {
        var row = jdbc.queryForList("SELECT comm_sn, user_sn, post_ttl FROM knowra_post.tbl_post WHERE post_sn = ? AND stat = ? AND actvtn_yn = 'Y'", postSn, HELD);
        if (row.isEmpty()) throw new IllegalArgumentException("이미 처리된 글이에요.");
        long commSn = ((Number) row.get(0).get("comm_sn")).longValue(), author = ((Number) row.get(0).get("user_sn")).longValue();
        String title = String.valueOf(row.get(0).get("post_ttl"));
        requireManager(commSn, me);
        int n = approve
                // 올라가는 때를 지금으로 — 쓴 시각에 두면 최신 피드에서 이미 묻혀 있다(예약 글과 같다)
                ? jdbc.update("UPDATE knowra_post.tbl_post SET stat = 'ACTIVE', frst_crt_dt = ? WHERE post_sn = ? AND stat = ?", LocalDateTime.now(), postSn, HELD)
                : jdbc.update("UPDATE knowra_post.tbl_post SET actvtn_yn = 'N', stat = 'DELETED' WHERE post_sn = ? AND stat = ?", postSn, HELD);
        if (n == 0) throw new IllegalArgumentException("이미 처리된 글이에요.");
        modLog.record(commSn, me, approve ? ModLogService.HOLD_APPROVE : ModLogService.HOLD_REJECT, author, postSn, title);
        String commName = jdbc.queryForObject("SELECT comm_dspl_nm FROM knowra_community.tbl_comm WHERE comm_sn = ?", String.class, commSn);
        events.publishEvent(new NotifTriggerEvent(author, null, approve ? "HOLD_APPROVED" : "HOLD_REJECTED",
                approve ? postSn : commSn, approve ? "POST" : "COMM", commName + "|" + title));
        return approve ? postSn : null;
    }

    /** 새로 잡아 둔 글이 생겼다 — 운영진에게 한 번 알린다 */
    public void notifyManagers(long commSn, long postSn, long author) {
        String commName = jdbc.queryForObject("SELECT comm_dspl_nm FROM knowra_community.tbl_comm WHERE comm_sn = ?", String.class, commSn);
        for (Long m : jdbc.queryForList("SELECT user_sn FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND stat = 'ACTIVE' AND role IN ('OWNER','ADMIN')", Long.class, commSn))
            events.publishEvent(new NotifTriggerEvent(m, null, "HOLD_NEW", commSn, "COMM", commName));
    }

    /** 멤버가 됐다(가입·승인·초대·차단 해제) — 검토 기간은 이때부터 센다 */
    public void markJoined(long commSn, long userSn) {
        jdbc.update("UPDATE knowra_community.tbl_comm_mbr SET joined_at = ? WHERE comm_sn = ? AND user_sn = ?", LocalDateTime.now(), commSn, userSn);
    }

    private void requireManager(long commSn, long me) {
        String role = jdbc.query("SELECT role FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE'",
                rs -> rs.next() ? rs.getString(1) : null, commSn, me);
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) throw new IllegalArgumentException("운영진만 할 수 있어요.");
    }
}
