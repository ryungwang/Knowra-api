package com.knowra.safety.moderation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoModerationTest {

    private static final String BODY = """
            {"id":"modr-1","model":"omni-moderation-latest","results":[
              {"flagged":false,"categories":{"hate":false,"sexual":false},"category_scores":{"hate":0.01,"sexual":0.02}},
              {"flagged":true,"categories":{"hate":true,"harassment":true,"sexual":false},"category_scores":{"hate":0.81,"harassment":0.93,"sexual":0.1}},
              {"flagged":true,"categories":{"sexual":true,"sexual/minors":true},"category_scores":{"sexual":0.99,"sexual/minors":0.42}}
            ]}""";

    @Test
    void 응답을_순서대로_읽고_점수_높은_순으로_둔다() {
        List<TextModerator.Verdict> v = OpenAiTextModerator.parse(BODY, 3);
        assertThat(v.get(0).flagged()).isFalse();
        assertThat(v.get(1).flags()).extracting(TextModerator.Flag::category).containsExactly("harassment", "hate");
        assertThat(v.get(2).flags()).extracting(TextModerator.Flag::category).containsExactly("sexual", "sexual/minors");
    }

    @Test
    void 결과_수가_다르면_실패로_본다() {
        assertThatThrownBy(() -> OpenAiTextModerator.parse(BODY, 2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 분류를_신고_사유로() {
        List<TextModerator.Verdict> v = OpenAiTextModerator.parse(BODY, 3);
        assertThat(AutoModerationJob.reasonOf(v.get(1))).isEqualTo("ABUSE");
        assertThat(AutoModerationJob.reasonOf(v.get(2))).as("아동 대상은 점수가 낮아도 아동 안전").isEqualTo("CHILD_SAFETY");
        assertThat(AutoModerationJob.reasonOf(verdict("sexual"))).isEqualTo("SEXUAL");
        assertThat(AutoModerationJob.reasonOf(verdict("illicit/violent"))).isEqualTo("ILLEGAL");
        assertThat(AutoModerationJob.reasonOf(verdict("self-harm/intent"))).isEqualTo("OTHER");
    }

    @Test
    void 관리자에게_보일_설명() {
        assertThat(AutoModerationJob.detailOf(OpenAiTextModerator.parse(BODY, 3).get(1))).isEqualTo("AI · harassment 93% · hate 81%");
    }

    @Test
    void 보낼_글은_태그를_지우고_자른다() {
        assertThat(AutoModerationJob.text("제목", "<p>본문&nbsp;<b>굵게</b></p>")).isEqualTo("제목\n본문 굵게");
        assertThat(AutoModerationJob.text(null, "x".repeat(5000))).hasSize(AutoModerationJob.MAX_CHARS);
    }

    private static TextModerator.Verdict verdict(String cat) { return new TextModerator.Verdict(List.of(new TextModerator.Flag(cat, 0.9))); }
}
