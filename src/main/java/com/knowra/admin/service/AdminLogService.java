package com.knowra.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사이트 관리자 조치 기록 — 동작과 같은 트랜잭션에 쓴다(있으면). 시각은 JVM 에서 넘긴다(DB 기본값은 세션 시간대를 탄다).
 * 지우는 길은 두지 않는다 — 기록이 지워지면 기록이 아니다.
 */
@Service
@RequiredArgsConstructor
public class AdminLogService {

    private static final int PAGE = 40;
    private final JdbcTemplate jdbc;

    public void record(long admin, String action, String targetKind, Long targetSn, String detail) {
        if (detail != null && detail.length() > 300) detail = detail.substring(0, 299) + "…";
        jdbc.update("INSERT INTO knowra_com.tbl_admin_log (admin_sn, action, target_kind, target_sn, detail, created_at) VALUES (?,?,?,?,?,?)",
                admin, action, targetKind, targetSn, detail, LocalDateTime.now());
    }

    public Map<String, Object> list(Long cursor) {
        String sql = """
                SELECT l.log_sn, l.action, l.target_kind, l.target_sn, l.detail, l.created_at, a.nick_name AS admin_nick, a.name AS admin_name,
                       CASE WHEN l.target_kind = 'USER' THEN (SELECT COALESCE(NULLIF(u.name, ''), u.nick_name) FROM knowra_user.tbl_user u WHERE u.user_sn = l.target_sn) END AS target_user
                FROM knowra_com.tbl_admin_log l JOIN knowra_user.tbl_user a ON a.user_sn = l.admin_sn""" + (cursor == null ? "" : " WHERE l.log_sn < ?") + " ORDER BY l.log_sn DESC LIMIT ?";
        List<Map<String, Object>> rows = cursor == null ? jdbc.queryForList(sql, PAGE + 1) : jdbc.queryForList(sql, cursor, PAGE + 1);
        boolean more = rows.size() > PAGE;
        if (more) rows = rows.subList(0, PAGE);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("logSn", r.get("log_sn")); m.put("action", r.get("action"));
            m.put("targetKind", r.get("target_kind")); m.put("targetSn", r.get("target_sn"));
            m.put("detail", r.get("detail")); m.put("createdAt", r.get("created_at"));
            m.put("admin", r.get("admin_name") != null && !String.valueOf(r.get("admin_name")).isBlank() ? r.get("admin_name") : r.get("admin_nick"));
            m.put("targetUser", r.get("target_user"));
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        out.put("nextCursor", more ? rows.get(rows.size() - 1).get("log_sn") : null);
        return out;
    }
}
