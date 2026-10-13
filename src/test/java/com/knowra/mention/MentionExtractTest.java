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
}
