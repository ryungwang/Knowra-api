package com.knowra.wiki;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WikiSlugTest {
    @Test
    void 제목으로_주소_이름() {
        assertThat(WikiService.slugOf("자주 묻는 질문 (FAQ)")).isEqualTo("자주-묻는-질문-faq");
        assertThat(WikiService.slugOf("  !!!  ")).isEqualTo("page");
        assertThat(WikiService.slugOf("Getting Started 101")).isEqualTo("getting-started-101");
    }
}
