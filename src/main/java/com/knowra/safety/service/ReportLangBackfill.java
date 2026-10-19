package com.knowra.safety.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** V69 이전 신고(댓글·메시지·사용자)에 언어를 채운다. 서버가 뜰 때 한 번, 500개씩. 새 신고는 넣을 때 정한다. */
@Slf4j
@Component
@RequiredArgsConstructor
class ReportLangBackfill {

    private final JdbcTemplate jdbc;
    private final ReportLang reportLang;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        int total = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT report_sn, target_kind, target_sn FROM knowra_user.tbl_report WHERE lang IS NULL ORDER BY report_sn LIMIT 500");
            if (rows.isEmpty()) break;
            for (Map<String, Object> r : rows)
                jdbc.update("UPDATE knowra_user.tbl_report SET lang = ? WHERE report_sn = ?",
                        reportLang.of((String) r.get("target_kind"), ((Number) r.get("target_sn")).longValue()), r.get("report_sn"));
            total += rows.size();
        }
        if (total > 0) log.info("[LANG] 신고 언어 채움 {}건", total);
    }
}
