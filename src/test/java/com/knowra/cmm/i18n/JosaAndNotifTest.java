package com.knowra.cmm.i18n;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JosaAndNotifTest {

    @Test
    void 받침을_보고_조사를_고른다() {
        assertThat(Josa.resolve("deer님이(가) 왔어요")).isEqualTo("deer님이 왔어요");
        assertThat(Josa.resolve("누군가이(가) 왔어요")).isEqualTo("누군가가 왔어요");
        assertThat(Josa.resolve("모임 「번개 모임」이(가) 곧")).isEqualTo("모임 「번개 모임」이 곧");
        assertThat(Josa.resolve("모임 「독서 스터디」이(가) 곧")).isEqualTo("모임 「독서 스터디」가 곧");
        assertThat(Josa.resolve("새 뱃지 「첫 글」을(를) 얻었어요")).isEqualTo("새 뱃지 「첫 글」을 얻었어요");
        assertThat(Josa.resolve("「첫 글」 외 3개을(를)")).isEqualTo("「첫 글」 외 3개를");
        assertThat(Josa.resolve("「Books」이(가)")).isEqualTo("「Books」가");
    }

    @Test
    void 날짜는_받는_사람의_언어와_시간대로() {
        var t = java.time.LocalDateTime.of(2026, 10, 18, 23, 25);
        String ko = Fmt.dateTime(t, "ko", java.time.ZoneId.systemDefault().getId());
        String en = Fmt.dateTime(t, "en", java.time.ZoneId.systemDefault().getId());
        assertThat(ko).contains("10").contains("18");
        assertThat(en).contains("Oct").contains("18");
        String ny = Fmt.dateTime(t, "en", "America/New_York");
        assertThat(ny).isNotEqualTo(en);   // 시간대가 다르면 시각이 다르다
    }
}
