package com.knowra.post.service;

import com.knowra.cmm.i18n.LangDetect;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * V61 이전 글에 언어를 채운다(LANG IS NULL). 서버가 뜰 때 한 번, 500개씩. 판단 못 한 글은 「und」로 표시해 다시 보지 않는다.
 * 새 글·고친 글은 PostService 가 저장할 때 정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PostLangBackfill {

    private final JdbcTemplate jdbc;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        int total = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT post_sn, post_ttl, post_cntnt FROM knowra_post.tbl_post WHERE lang IS NULL ORDER BY post_sn LIMIT 500");
            if (rows.isEmpty()) break;
            for (Map<String, Object> r : rows) {
                String lang = LangDetect.detect((String) r.get("post_ttl"), (String) r.get("post_cntnt"));
                jdbc.update("UPDATE knowra_post.tbl_post SET lang = ? WHERE post_sn = ?", lang == null ? "und" : lang, r.get("post_sn"));
            }
            total += rows.size();
        }
        if (total > 0) log.info("[LANG] 글 언어 채움 {}건", total);
    }
}
