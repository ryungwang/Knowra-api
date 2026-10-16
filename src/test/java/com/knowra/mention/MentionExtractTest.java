package com.knowra.mention;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MentionExtractTest {

    @Test
    void 닉네임을_순서대로_중복없이() {
        assertThat(MentionService.extract("@deer 안녕 @hwangtrader 그리고 @deer")).containsExactly("deer", "hwangtrader");
    }

    @Test
    void 이메일과_문장끝_마침표는_멘션이_아니다() {
        assertThat(MentionService.extract("메일은 a@deer.com 으로. 고마워요 @채팅친구.")).containsExactly("채팅친구");
    }

    @Test
    void HTML_본문에서도_찾는다() {
        assertThat(MentionService.extract("<p>질문이요 <b>@choicrypto</b>님</p>")).containsExactly("choicrypto");
    }

    @Test
    void 한_글자와_빈_골뱅이는_무시() {
        assertThat(MentionService.extract("@ a @x @ok")).containsExactly("ok");
    }

    @Test
    void 붙여_쓴_조사는_후보에서_떼어_본다() {
        assertThat(MentionService.variants("deer님")).containsExactly("deer님", "deer");
        assertThat(MentionService.variants("채팅친구에게")).contains("채팅친구에게", "채팅친구");
        assertThat(MentionService.variants("ok")).containsExactly("ok");
    }

    @Test
    void 어느_언어의_닉네임이든_멘션된다() {
        assertThat(MentionService.extract("@田中太郎 さん、@Müller と @Анна")).containsExactly("田中太郎", "Müller", "Анна");
    }

    @Test
    void 띄어쓰지_않는_글자는_앞부분을_후보로() {
        assertThat(MentionService.variants("田中さん")).contains("田中");
        assertThat(MentionService.variants("小明你好")).contains("小明", "小明你");
        // 로마자는 앞부분을 후보로 만들지 않는다 — 「@deerhi」 가 deer 에게 가면 안 된다
        assertThat(MentionService.variants("deerhi")).containsExactly("deerhi");
    }
}
