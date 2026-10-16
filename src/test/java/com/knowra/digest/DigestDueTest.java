package com.knowra.digest;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DigestDueTest {

    @Test
    void 각자의_월요일_오전_9시에만() {
        Instant seoulMon9 = Instant.parse("2026-10-19T00:00:00Z");   // 서울 월 09:00 = 뉴욕 일 20:00
        assertThat(DigestService.dueAt(seoulMon9, "Asia/Seoul")).isTrue();
        assertThat(DigestService.dueAt(seoulMon9, "America/New_York")).isFalse();
        Instant nyMon9 = Instant.parse("2026-10-19T13:00:00Z");      // 뉴욕 월 09:00(서머타임)
        assertThat(DigestService.dueAt(nyMon9, "America/New_York")).isTrue();
        assertThat(DigestService.dueAt(nyMon9, "Asia/Seoul")).isFalse();
        assertThat(DigestService.dueAt(seoulMon9, null)).isTrue();   // 설정이 없으면 서울
    }

    @Test
    void 한_번_눌러_그만_받기_머리글() {   // RFC 8058 — Gmail·Yahoo 대량 발송 요건
        var h = DigestService.oneClickHeaders("https://knowra.example/api/digest/oneclick?u=1&t=x");
        assertThat(h.get("List-Unsubscribe")).isEqualTo("<https://knowra.example/api/digest/oneclick?u=1&t=x>");
        assertThat(h.get("List-Unsubscribe-Post")).isEqualTo("List-Unsubscribe=One-Click");
    }
}
