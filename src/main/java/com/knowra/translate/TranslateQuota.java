package com.knowra.translate;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * 달마다 보낸 글자 수 상한 — 무료 한도를 넘겨 요금이 나가지 않게. 넘으면 그달은 번역하지 않는다
 * (화면은 원문과 브라우저 번역 단추로 돌아간다). Azure F0 는 매달 200만 자 — 기본 상한은 그보다 조금 아래.
 */
@Component
@RequiredArgsConstructor
public class TranslateQuota {

    private final JdbcTemplate jdbc;

    @Value("${knowra.translate.monthly-char-limit:1900000}")
    private long limit;

    /** 이만큼 써도 되면 미리 잡아 두고 true. 모자라면 false(아무것도 잡지 않는다) */
    public boolean reserve(long chars) {
        String month = YearMonth.now(ZoneOffset.UTC).toString();
        jdbc.update("INSERT INTO knowra_com.tbl_translate_usage (month, chars) VALUES (?, 0) ON CONFLICT (month) DO NOTHING", month);
        return jdbc.update("UPDATE knowra_com.tbl_translate_usage SET chars = chars + ? WHERE month = ? AND chars + ? <= ?", chars, month, chars, limit) == 1;
    }

    public long usedThisMonth() {
        Long n = jdbc.query("SELECT chars FROM knowra_com.tbl_translate_usage WHERE month = ?", rs -> rs.next() ? rs.getLong(1) : 0L,
                YearMonth.now(ZoneOffset.UTC).toString());
        return n == null ? 0 : n;
    }

    public long limit() { return limit; }
}
