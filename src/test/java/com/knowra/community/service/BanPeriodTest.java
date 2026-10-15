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
        assertThat(CommunityService.banDetail(null, null)).isEqualTo("영구");
        assertThat(CommunityService.banDetail(NOW.plusDays(3), "도배")).isEqualTo("10월 18일 12:00까지 · 사유: 도배");
    }
}
