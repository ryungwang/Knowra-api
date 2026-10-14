package com.knowra.community.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 커뮤니티 통계 — 운영진만. 최근 30일.
 *
 * <p>날짜는 저장된 그대로(JVM 시각 = 한국 시간)로 자른다. 하루 단위 시계열은 빈 날도 0 으로 채워 보낸다 —
 * 화면이 빠진 날을 이어 그리면 「그날도 글이 있었던」 것처럼 보인다.
 * 익명 커뮤니티는 「많이 쓴 멤버」를 내지 않는다 — 활동량 순위가 누가 쓴 글인지 짐작하게 한다.
 */
@Service
@RequiredArgsConstructor
public class CommunityStatsService {

    private static final int DAYS = 30;
    private final JdbcTemplate jdbc;

    public Map<String, Object> stats(long commSn, long me) {
        String role = jdbc.query("SELECT role FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE'",
                rs -> rs.next() ? rs.getString(1) : null, commSn, me);
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) throw new IllegalArgumentException("운영진만 볼 수 있어요.");
        boolean anonymous = "anonymous".equals(jdbc.query("SELECT prvcy_stng FROM knowra_community.tbl_comm WHERE comm_sn = ?",
                rs -> rs.next() ? rs.getString(1) : null, commSn));

        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(DAYS - 1).atStartOfDay(), week = today.minusDays(6).atStartOfDay();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", DAYS);

        Map<String, Object> members = new LinkedHashMap<>();
        members.put("total", count("SELECT COUNT(*) FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND stat = 'ACTIVE'", commSn));
        // 가입 = 들어온 적이 있는 줄(지금 활성이거나, 들어왔다 나간·내보내진). 신청만 하고 대기·거절된 줄은 뺀다
        String joined = "FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND stat IN ('ACTIVE','WITHDRAWN','BANNED') AND frst_crt_dt >= ?";
        members.put("joined7", count("SELECT COUNT(*) " + joined, commSn, week));
        members.put("joined30", count("SELECT COUNT(*) " + joined, commSn, from));
        members.put("left30", count("SELECT COUNT(*) FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND stat IN ('WITHDRAWN','BANNED') AND mdfcn_dt >= ?", commSn, from));
        members.put("pending", count("SELECT COUNT(*) FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND stat = 'PENDING'", commSn));
        out.put("members", members);

        String posts = "FROM knowra_post.tbl_post p WHERE p.comm_sn = ? AND p.actvtn_yn = 'Y' AND p.stat = 'ACTIVE' AND p.frst_crt_dt >= ?";
        String cmts = "FROM knowra_post.tbl_post_cmt c JOIN knowra_post.tbl_post p ON p.post_sn = c.post_sn " +
                "WHERE p.comm_sn = ? AND c.actvtn_yn = 'Y' AND c.stat = 'ACTIVE' AND c.frst_crt_dt >= ?";

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("posts30", count("SELECT COUNT(*) " + posts, commSn, from));
        activity.put("comments30", count("SELECT COUNT(*) " + cmts, commSn, from));
        // 활동한 멤버 = 지난 7일 글이나 댓글을 쓴 사람(눈팅은 셀 수단이 없다)
        activity.put("activeMembers7", count("SELECT COUNT(DISTINCT u) FROM (SELECT p.user_sn u " + posts + " UNION SELECT c.user_sn " + cmts + ") x",
                commSn, week, commSn, week));
        out.put("activity", activity);

        out.put("daily", daily(commSn, from, today, posts, cmts, joined));
        out.put("hours", hours(commSn, from, posts, cmts));

        out.put("topPosts", jdbc.queryForList("""
                SELECT p.post_sn AS "postSn", p.post_ttl AS title, p.like_cnt AS likes, p.cmt_cnt AS comments, p.view_cnt AS views
                """ + posts + " ORDER BY (p.like_cnt * 3 + p.cmt_cnt * 5 + p.view_cnt * 0.05) DESC, p.post_sn DESC LIMIT 5", commSn, from));

        if (!anonymous) {
            // ⚠️ 텍스트 블록은 줄 끝 공백을 지운다 — 「0 c 」 뒤에 붙이면 「0 cFROM」 이 된다. 공백은 따로 붙인다
            out.put("topMembers", jdbc.queryForList("""
                    SELECT u.nick_name AS "nickName", u.name AS name, SUM(x.p) AS posts, SUM(x.c) AS comments
                    FROM (SELECT p.user_sn u, 1 p, 0 c""" + " " + posts + " UNION ALL SELECT c.user_sn, 0, 1 " + cmts + """
                    ) x JOIN knowra_user.tbl_user u ON u.user_sn = x.u AND u.actvtn_yn = 'Y'
                    GROUP BY u.user_sn, u.nick_name, u.name ORDER BY SUM(x.p) * 3 + SUM(x.c) DESC LIMIT 5""", commSn, from, commSn, from));
        }
        out.put("anonymous", anonymous);
        return out;
    }

    private List<Map<String, Object>> daily(long commSn, LocalDateTime from, LocalDate today, String posts, String cmts, String joined) {
        Map<LocalDate, long[]> byDay = new HashMap<>();
        accumulate(byDay, "SELECT CAST(p.frst_crt_dt AS date) d, COUNT(*) n " + posts + " GROUP BY 1", 0, commSn, from);
        accumulate(byDay, "SELECT CAST(c.frst_crt_dt AS date) d, COUNT(*) n " + cmts + " GROUP BY 1", 1, commSn, from);
        accumulate(byDay, "SELECT CAST(frst_crt_dt AS date) d, COUNT(*) n " + joined + " GROUP BY 1", 2, commSn, from);
        List<Map<String, Object>> out = new ArrayList<>();
        for (LocalDate d = from.toLocalDate(); !d.isAfter(today); d = d.plusDays(1)) {
            long[] v = byDay.getOrDefault(d, new long[3]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.toString()); m.put("posts", v[0]); m.put("comments", v[1]); m.put("joins", v[2]);
            out.add(m);
        }
        return out;
    }

    private void accumulate(Map<LocalDate, long[]> byDay, String sql, int slot, Object... args) {
        jdbc.query(sql, rs -> {
            byDay.computeIfAbsent(rs.getDate("d").toLocalDate(), k -> new long[3])[slot] = rs.getLong("n");
        }, args);
    }

    /** 시간대(0~23시)별 글+댓글 — 공지·모임 시간을 정할 때 본다 */
    private long[] hours(long commSn, LocalDateTime from, String posts, String cmts) {
        long[] h = new long[24];
        jdbc.query("SELECT CAST(EXTRACT(HOUR FROM t) AS int) h, COUNT(*) n FROM (SELECT p.frst_crt_dt t " + posts +
                        " UNION ALL SELECT c.frst_crt_dt " + cmts + ") x GROUP BY 1",
                rs -> { h[rs.getInt("h")] = rs.getLong("n"); }, commSn, from, commSn, from);
        return h;
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
