package com.knowra.translate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslateTest {

    @Test
    void 댓글의_멘션_링크는_번역하지_않고_스포일러는_표시를_지킨다() {
        String out = TranslateMarkup.plainOut("@민수 이거 봐 https://a.com/x?y=1&z=2 ||범인은 집사|| <b>\n둘째 줄");
        assertThat(out).contains("<span class=\"notranslate\">@민수</span>")
                .contains("<span class=\"notranslate\">https://a.com/x?y=1&amp;z=2</span>")
                .contains("<span class=\"ksp\">범인은 집사</span>")
                .contains("&lt;b&gt;<br>둘째 줄");
        // 업체가 겉 글자만 바꿔 돌려줬다고 치고 되돌린다
        String back = TranslateMarkup.plainBack(out.replace("이거 봐", "look at this").replace("범인은 집사", "the butler did it").replace("둘째 줄", "line two"));
        assertThat(back).isEqualTo("@민수 look at this https://a.com/x?y=1&z=2 ||the butler did it|| <b>\nline two");
    }

    @Test
    void 메일_주소의_골뱅이는_멘션이_아니다() {
        assertThat(TranslateMarkup.plainOut("mail a@b.com")).doesNotContain("notranslate\">@b");
    }

    @Test
    void 글_본문의_코드는_번역하지_않고_돌아오면_원래_모양() {
        String html = "<p>설명</p><pre><code class=\"language-js\">const a = 1</code></pre><p>||스포||</p>";
        String out = TranslateMarkup.htmlOut(html);
        assertThat(out).contains("<pre class=\"notranslate\">").contains("<code class=\"notranslate language-js\">").contains("<span class=\"ksp\">스포</span>");
        assertThat(TranslateMarkup.htmlBack(out)).isEqualTo(html);
    }

    @Test
    void 제목은_글자만() {
        assertThat(TranslateMarkup.titleBack(TranslateMarkup.titleOut("A & B <3"))).isEqualTo("A & B <3");
    }

    @Test
    void 확실히_같은_언어면_업체에_묻지_않는다() {
        assertThat(TranslationService.needs("ko", "ko")).isFalse();
        assertThat(TranslationService.needs("ko", "en")).isTrue();
        assertThat(TranslationService.needs("ja", "ja")).isFalse();
        assertThat(TranslationService.needs("latn", "en")).as("영어인지 스페인어인지 모른다").isTrue();
        assertThat(TranslationService.needs("zh", "zh-Hant")).as("간체일 수 있다").isTrue();
        assertThat(TranslationService.needs(null, "en")).as("너무 짧다").isFalse();
        // 업체가 알려 준 정확한 언어를 적어 둔 글
        assertThat(TranslationService.needs("en", "en")).isFalse();
        assertThat(TranslationService.needs("es", "en")).isTrue();
        assertThat(TranslationService.needs("zh-Hans", "zh-Hans")).isFalse();
        assertThat(TranslationService.needs("zh-Hans", "zh-Hant")).isTrue();
    }

    @Test
    void 업체가_본_원문_언어로_같은지() {
        assertThat(TranslationService.sameLang("en", "en")).isTrue();
        assertThat(TranslationService.sameLang("es", "en")).isFalse();
        assertThat(TranslationService.sameLang("zh-Hans", "zh-Hans")).isTrue();
        assertThat(TranslationService.sameLang("zh-Hans", "zh-Hant")).as("간체 → 번체는 바꿔 준다").isFalse();
        assertThat(TranslationService.sameLang(null, "en")).isFalse();
    }

    @Test
    void 업체_응답을_읽는다() {
        String body = """
                [{"detectedLanguage":{"language":"ko","score":1.0},"translations":[{"text":"Hello","to":"en"}]},
                 {"detectedLanguage":{"language":"ja","score":0.9},"translations":[{"text":"World","to":"en"}]}]""";
        List<Translator.Out> out = AzureTranslator.parse(body, 2);
        assertThat(out).containsExactly(new Translator.Out("Hello", "ko"), new Translator.Out("World", "ja"));
        assertThatThrownBy(() -> AzureTranslator.parse(body, 3)).isInstanceOf(IllegalStateException.class);
    }
}
