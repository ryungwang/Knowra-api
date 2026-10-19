package com.knowra.safety.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportLangTest {

    @Test
    void 본문_글자로_가린다() {
        assertThat(ReportLang.ofText("이 댓글은 너무 심하네요")).isEqualTo("ko");
        assertThat(ReportLang.ofText("これはひどいコメントです")).isEqualTo("ja");
        assertThat(ReportLang.ofText("这条评论太过分了")).isEqualTo("zh");
        assertThat(ReportLang.ofText("this comment is awful")).isEqualTo("latn");
        assertThat(ReportLang.ofText("ㅋ")).isEqualTo("und");   // 너무 짧으면 모른다 — 모두에게 보인다
    }

    @Test
    void 화면_언어를_글자_체계로() {
        assertThat(ReportLang.ofUiLang("zh-Hant")).isEqualTo("zh");
        assertThat(ReportLang.ofUiLang("en")).isEqualTo("latn");
        assertThat(ReportLang.ofUiLang("ja")).isEqualTo("ja");
    }

    @Test
    void 모르는_값은_버린다() {
        assertThat(ReportLang.parse("ko, latn,xx")).containsExactly("ko", "latn");
        assertThat(ReportLang.parse(List.of("zh", "und"))).containsExactly("zh");
        assertThat(ReportLang.parse(null)).isEmpty();
        assertThat(ReportLang.parse("")).isEmpty();
    }
}
