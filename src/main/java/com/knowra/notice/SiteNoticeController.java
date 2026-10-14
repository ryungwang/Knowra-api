package com.knowra.notice;

import com.knowra.admin.service.AdminLogService;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 사이트 전체 공지 배너. 읽기(/api/notice/current)는 누구나, 올리기·내리기는 관리자(/api/admin/** 는 ROLE_ADMIN).
 * 조치는 관리자 기록에 남는다.
 */
@RestController
@RequiredArgsConstructor
public class SiteNoticeController {

    private final JdbcTemplate jdbc;
    private final AdminLogService adminLog;

    @GetMapping("/api/notice/current")
    public ResultVO current() {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT notice_sn AS "noticeSn", message, link, level FROM knowra_com.tbl_site_notice
                WHERE active AND starts_at <= ? AND (ends_at IS NULL OR ends_at > ?) ORDER BY notice_sn DESC LIMIT 1""", now, now);
        ResultVO r = new ResultVO();
        r.putResult("notice", rows.isEmpty() ? null : rows.get(0));
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    @PostMapping("/api/admin/console/notices")
    public ResultVO list() {
        ResultVO r = new ResultVO();
        r.putResult("list", jdbc.queryForList("""
                SELECT n.notice_sn AS "noticeSn", n.message, n.link, n.level, n.starts_at AS "startsAt", n.ends_at AS "endsAt", n.active,
                       n.created_at AS "createdAt", COALESCE(NULLIF(u.name, ''), u.nick_name) AS "createdBy"
                FROM knowra_com.tbl_site_notice n LEFT JOIN knowra_user.tbl_user u ON u.user_sn = n.created_by
                ORDER BY n.notice_sn DESC LIMIT 30"""));
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** body: message(300자), link?(/경로 또는 https://), level(INFO|WARN), endsOn?(yyyy-MM-dd — 그날 끝까지) */
    @PostMapping("/api/admin/console/noticeCreate")
    public ResultVO create(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        String message = String.valueOf(b.getOrDefault("message", "")).strip();
        String link = b.get("link") == null ? "" : String.valueOf(b.get("link")).strip();
        String level = "WARN".equals(b.get("level")) ? "WARN" : "INFO";
        if (message.isEmpty() || message.length() > 300) return fail(r, "공지 문구를 1~300자로 써 주세요.");
        // 사이트 안 경로(//로 시작하는 다른 사이트 경로는 빼고)나 https 만
        if (!link.isEmpty() && !(link.matches("^/(?!/)\\S*$") || link.matches("^https://\\S+$")) || link.length() > 300)
            return fail(r, "링크는 사이트 안 경로(/로 시작)나 https:// 주소만 넣을 수 있어요.");
        LocalDateTime ends = null;
        if (b.get("endsOn") != null && !String.valueOf(b.get("endsOn")).isBlank()) {
            try { ends = LocalDate.parse(String.valueOf(b.get("endsOn"))).plusDays(1).atStartOfDay(); }
            catch (Exception e) { return fail(r, "끝나는 날을 확인해 주세요."); }
            if (!ends.isAfter(LocalDateTime.now())) return fail(r, "끝나는 날은 오늘이나 그 뒤여야 해요.");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO knowra_com.tbl_site_notice (message, link, level, starts_at, ends_at, created_by, created_at) VALUES (?,?,?,?,?,?,?)",
                message, link.isEmpty() ? null : link, level, now, ends, me, now);
        Long sn = jdbc.queryForObject("SELECT MAX(notice_sn) FROM knowra_com.tbl_site_notice WHERE created_by = ?", Long.class, me);
        adminLog.record(me, "NOTICE_CREATE", "NOTICE", sn, message);
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** body: noticeSn — 내리기 */
    @PostMapping("/api/admin/console/noticeEnd")
    public ResultVO end(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        long sn = Long.parseLong(String.valueOf(b.get("noticeSn")));
        int n = jdbc.update("UPDATE knowra_com.tbl_site_notice SET active = FALSE WHERE notice_sn = ? AND active", sn);
        if (n == 1) adminLog.record(me, "NOTICE_END", "NOTICE", sn, null);
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    private static ResultVO fail(ResultVO r, String msg) {
        r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
        r.setResultMessage(msg);
        return r;
    }
}
