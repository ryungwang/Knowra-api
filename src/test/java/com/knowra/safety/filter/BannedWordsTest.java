package com.knowra.safety.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BannedWordsTest {

    private static String hit(String text, String... words) { return BannedWords.of(List.of(words)).find(text); }

    @Test
    void 한국어는_예전처럼_들어_있으면_막는다() {
        assertThat(hit("야 시 . 발 뭐야", "시발")).isEqualTo("시발");
        assertThat(hit("시발놈아", "시발")).isEqualTo("시발");
        assertThat(hit("시작했다", "시발")).isNull();
    }

    @Test
    void 영어는_낱말로_있을_때만_막는다() {
        assertThat(hit("what an ass", "ass")).isEqualTo("ass");
        assertThat(hit("first class pass, ask the assistant", "ass")).isNull();
        assertThat(hit("Hello, ASS!", "ass")).isEqualTo("ass");
    }

    @Test
    void 영어_우회를_잡는다() {
        assertThat(hit("ｆｕｃｋ", "fuck")).as("전각").isEqualTo("fuck");
        assertThat(hit("fück off", "fuck")).as("악센트").isEqualTo("fuck");
        assertThat(hit("fuсk", "fuck")).as("키릴 с").isEqualTo("fuck");
        assertThat(hit("f u c k", "fuck")).as("한 글자씩").isEqualTo("fuck");
        assertThat(hit("f.u.c.k this", "fuck")).as("점으로").isEqualTo("fuck");
        assertThat(hit("fuuuuck", "fuck")).as("반복").isEqualTo("fuck");
        assertThat(hit("sh1t", "shit")).as("숫자").isEqualTo("shit");
        assertThat(hit("$hit happens", "shit")).as("기호").isEqualTo("shit");
        assertThat(hit("sh!t", "shit")).isEqualTo("shit");
        assertThat(hit("@ss", "ass")).isEqualTo("ass");
    }

    @Test
    void 평범한_글은_통과한다() {
        assertThat(hit("I scored 2024 points! Great.", "shit", "ass", "fuck")).isNull();
        assertThat(hit("mail me at a@b.com", "ass")).isNull();
        assertThat(hit("Hello!", "helloi")).isNull();
    }

    @Test
    void 여러_낱말과_숫자만_있는_금칙어() {
        assertThat(hit("just kill   yourself", "kill yourself")).isEqualTo("kill yourself");
        assertThat(hit("killing yourself slowly", "kill yourself")).isNull();
        assertThat(hit("code 88 here", "88")).isEqualTo("88");
        assertThat(hit("code 1888", "88")).isNull();
    }

    @Test
    void 붙여_쓰는_글_속의_영어_낱말도_꺼낸다() {
        assertThat(hit("这是fuck的", "fuck")).isEqualTo("fuck");
        assertThat(hit("ばかやろう", "ばか")).isEqualTo("ばか");
        assertThat(hit("傻逼", "傻逼")).isEqualTo("傻逼");
    }

    @Test
    void 일본어_탁점은_지우지_않는다() {
        assertThat(hit("かか", "がが")).isNull();
        assertThat(hit("がが", "がが")).isEqualTo("がが");
    }

    @Test
    void 러시아어는_낱말_단위() {
        assertThat(BannedWords.modeOf("блять")).isEqualTo(BannedWords.Mode.WORD);
        assertThat(hit("ну блять", "блять")).isEqualTo("блять");
        assertThat(hit("ну БЛЯТЬ!", "блять")).isEqualTo("блять");
    }

    @Test
    void 비교할_게_없는_낱말은_뺀다() {
        assertThat(hit("a ... b", "...", "a")).isNull();
    }
}
