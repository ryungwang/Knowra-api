package com.knowra.cmm.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MsgTest {

    @AfterEach
    void reset() { Msg.useTable(Map.of()); }

    @Test
    void 언어_태그를_다섯_개_중_하나로() {
        assertThat(Lang.fromHeader("ja,en-US;q=0.9")).isEqualTo("ja");
        assertThat(Lang.fromHeader("zh-TW,zh;q=0.9")).isEqualTo("zh-Hant");
        assertThat(Lang.fromHeader("zh-HK")).isEqualTo("zh-Hant");
        assertThat(Lang.fromHeader("zh-CN")).isEqualTo("zh-Hans");
        assertThat(Lang.fromHeader("zh-Hant")).isEqualTo("zh-Hant");
        assertThat(Lang.fromHeader("fr-FR,de;q=0.8")).isEqualTo("en");   // 모르는 언어는 영어
        assertThat(Lang.fromHeader("fr,ko;q=0.5")).isEqualTo("ko");       // 뒤에라도 아는 언어가 있으면 그것
        assertThat(Lang.fromHeader(null)).isEqualTo("en");
    }

    @Test
    void 원문이_키이고_변수를_채운다() {
        Msg.useTable(Map.of("「{0}」의 주인이에요.", Map.of("en", "You own “{0}”.", "zh-Hans", "你是「{0}」的所有者。")));
        assertThat(Msg.tl("ko", "「{0}」의 주인이에요.", "독서")).isEqualTo("「독서」의 주인이에요.");
        assertThat(Msg.tl("en", "「{0}」의 주인이에요.", "Books")).isEqualTo("You own “Books”.");
        assertThat(Msg.tl("zh-Hant", "「{0}」의 주인이에요.", "讀書")).isEqualTo("你是「讀書」的所有者。");   // 번체가 없으면 간체
        assertThat(Msg.tl("ja", "「{0}」의 주인이에요.", "x")).isEqualTo("You own “x”.");                  // 일본어가 없으면 영어
        assertThat(Msg.tl("en", "표에 없는 문장 {0}", 3)).isEqualTo("표에 없는 문장 3");                     // 없으면 원문
    }

    @Test
    void 작은따옴표가_깨지지_않는다() {
        Msg.useTable(Map.of("못 했어요.", Map.of("en", "It's {0}'s turn.")));
        assertThat(Msg.tl("en", "못 했어요.", "Ann")).isEqualTo("It's Ann's turn.");
    }
}
