package com.knowra.cmm.i18n;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LangDetectTest {
    @Test
    void 글자_체계로_언어를_가린다() {
        assertThat(LangDetect.detect("React 훅 정리", "<p>useEffect 쓰는 법을 정리했어요.</p>")).isEqualTo("ko");
        assertThat(LangDetect.detect("東京の天気", "<p>今日は晴れです。</p>")).isEqualTo("ja");
        assertThat(LangDetect.detect("北京天气", "<p>今天很好。</p>")).isEqualTo("zh");
        assertThat(LangDetect.detect("Weekend plans", "<p>Going hiking with friends.</p>")).isEqualTo("latn");
        assertThat(LangDetect.detect("ㅋ", "")).isNull();
        assertThat(LangDetect.commLang("ja")).isEqualTo("ja");
        assertThat(LangDetect.commLang("klingon")).isNull();
    }
}
