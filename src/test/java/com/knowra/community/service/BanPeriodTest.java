package com.knowra.community.service;

import com.knowra.community.entity.TblCommMbr;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BanPeriodTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    @Test
    void 기간은_정해진_값만_받고_0이나_빈값은_영구() {
        assertThat(CommunityService.banUntil(7, NOW)).isEqualTo(NOW.plusDays(7));
        assertThat(CommunityService.banUntil("30", NOW)).isEqualTo(NOW.plusDays(30));
        assertThat(CommunityService.banUntil(0, NOW)).isNull();
        assertThat(CommunityService.banUntil(null, NOW)).isNull();
        assertThatThrownBy(() -> CommunityService.banUntil(365, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CommunityService.banUntil("abc", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기간이_지난_강퇴는_풀린_것으로_본다() {
        TblCommMbr m = new TblCommMbr();
        m.setStat("BANNED");
        assertThat(m.bannedNow(NOW)).isTrue();                 // 영구
        m.setBanUntil(NOW.plusHours(1));
        assertThat(m.bannedNow(NOW)).isTrue();
        m.setBanUntil(NOW.minusMinutes(1));
        assertThat(m.bannedNow(NOW)).isFalse();
        m.setStat("ACTIVE");
        m.setBanUntil(null);
        assertThat(m.bannedNow(NOW)).isFalse();
    }

    @Test
    void 운영_기록_한줄() {
        // 운영 기록은 문장이 아니라 값(JSON) — 화면이 보는 사람 언어·시간대로 문장을 만든다
        assertThat(com.knowra.community.modlog.ModLogDetail.ban(null, null)).isEqualTo("{\"until\":null}");
        assertThat(com.knowra.community.modlog.ModLogDetail.ban(NOW.plusDays(3), "도배")).contains("\"until\":\"20").contains("\"reason\":\"도배\"");
    }
}
