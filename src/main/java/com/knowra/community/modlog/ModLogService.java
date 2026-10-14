package com.knowra.community.modlog;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 커뮤니티 운영 기록 — 운영진이 남의 것에 손댄 일만 남긴다(자기 글을 지운 건 운영이 아니다).
 *
 * <p>기록은 그 동작과 같은 트랜잭션에 쓴다(JdbcTemplate 이 JPA 트랜잭션의 연결을 같이 쓴다) — 동작이
 * 실패해 되돌려지면 기록도 남지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ModLogService {

    public static final String MEMBER_APPROVE = "MEMBER_APPROVE", MEMBER_REJECT = "MEMBER_REJECT", MEMBER_BAN = "MEMBER_BAN",
            MEMBER_UNBAN = "MEMBER_UNBAN", MEMBER_REMOVE = "MEMBER_REMOVE", ROLE_ADMIN = "ROLE_ADMIN", ROLE_MEMBER = "ROLE_MEMBER", OWNER_TRANSFER = "OWNER_TRANSFER",
            SETTINGS = "SETTINGS", RULES = "RULES", WIKI_SAVE = "WIKI_SAVE", WIKI_DELETE = "WIKI_DELETE",
            POST_EDIT = "POST_EDIT", POST_DELETE = "POST_DELETE", POST_PIN = "POST_PIN", POST_UNPIN = "POST_UNPIN",
            CMT_DELETE = "CMT_DELETE", EVENT_EDIT = "EVENT_EDIT", EVENT_CANCEL = "EVENT_CANCEL";

    private static final int PAGE = 30;

    private final JdbcTemplate jdbc;

    public void record(long commSn, long actor, String action, Long targetUser, Long postSn, String detail) {
        // 익명 커뮤니티: 글·댓글 기록이 작성자를 가리키면 운영진에게 익명이 깨진다
        if (postSn != null && targetUser != null && isAnonymous(commSn)) targetUser = null;
        if (detail != null && detail.length() > 300) detail = detail.substring(0, 299) + "…";
        jdbc.update("INSERT INTO knowra_community.tbl_comm_mod_log (comm_sn, actor_sn, action, target_user_sn, target_post_sn, detail) VALUES (?,?,?,?,?,?)",
                commSn, actor, action, targetUser, postSn, detail);
    }

    /** 운영진만. cursor = 이보다 오래된 logSn. */
    public Map<String, Object> list(long commSn, Long cursor, long me) {
        String role = jdbc.query("SELECT role FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE'",
                rs -> rs.next() ? rs.getString(1) : null, commSn, me);
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) throw new IllegalArgumentException("운영진만 볼 수 있어요.");
        // 「? IS NULL」 은 PostgreSQL 이 null 의 타입을 못 정해 실패한다 — 커서 조건을 붙였다 뗐다 한다
        String sql = """
                SELECT l.log_sn, l.action, l.detail, l.target_post_sn, l.created_at,
                       a.nick_name AS actor_nick, a.name AS actor_name, t.user_sn AS target_sn, t.nick_name AS target_nick, t.name AS target_name
                FROM knowra_community.tbl_comm_mod_log l
                JOIN knowra_user.tbl_user a ON a.user_sn = l.actor_sn
                LEFT JOIN knowra_user.tbl_user t ON t.user_sn = l.target_user_sn
                WHERE l.comm_sn = ?""" + (cursor == null ? "" : " AND l.log_sn < ?") + " ORDER BY l.log_sn DESC LIMIT ?";
        List<Map<String, Object>> rows = cursor == null
                ? jdbc.queryForList(sql, commSn, PAGE + 1)
                : jdbc.queryForList(sql, commSn, cursor, PAGE + 1);
        boolean more = rows.size() > PAGE;
        if (more) rows = rows.subList(0, PAGE);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("logSn", r.get("log_sn")); m.put("action", r.get("action")); m.put("detail", r.get("detail"));
            m.put("postSn", r.get("target_post_sn")); m.put("createdAt", r.get("created_at"));
            m.put("actor", Map.of("nickName", r.get("actor_nick"), "name", r.get("actor_name") == null ? "" : r.get("actor_name")));
            if (r.get("target_sn") != null)
                m.put("target", Map.of("nickName", r.get("target_nick"), "name", r.get("target_name") == null ? "" : r.get("target_name")));
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("nextCursor", more ? rows.get(rows.size() - 1).get("log_sn") : null);
        return out;
    }

    private boolean isAnonymous(long commSn) {
        return "anonymous".equals(jdbc.query("SELECT prvcy_stng FROM knowra_community.tbl_comm WHERE comm_sn = ?",
                rs -> rs.next() ? rs.getString(1) : null, commSn));
    }
}
