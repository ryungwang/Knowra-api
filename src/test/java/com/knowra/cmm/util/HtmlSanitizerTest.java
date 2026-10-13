package com.knowra.cmm.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    @Test
    void 스크립트와_이벤트_속성과_javascript_주소는_사라진다() {
        String dirty = "<p onclick=\"x()\">hi<img src=x onerror=alert(1)><script>alert(2)</script>"
                + "<a href=\"javascript:alert(3)\">링크</a><iframe src=\"https://evil\"></iframe>"
                + "<span style=\"position:fixed\">s</span><svg onload=alert(4)></svg></p>";
        String out = HtmlSanitizer.clean(dirty);
        assertThat(out).doesNotContain("onclick", "onerror", "script", "javascript:", "iframe", "style", "svg", "onload");
    }

    @Test
    void 편집기가_만드는_서식은_남는다() {
        String ok = "<h2>제목</h2><p><strong>굵게</strong> <em>기울임</em> <code>c</code></p>"
                + "<ul><li>하나</li></ul><blockquote><p>인용</p></blockquote>"
                + "<p><a href=\"https://example.com\" target=\"_blank\">링크</a></p>"
                + "<img src=\"http://localhost:8081/upload/editor/2026/10/14/1_0.png\">";
        String out = HtmlSanitizer.clean(ok);
        assertThat(out).contains("<h2>제목</h2>", "<strong>굵게</strong>", "<em>기울임</em>", "<li>하나</li>", "<blockquote>",
                "href=\"https://example.com\"", "rel=\"noopener noreferrer nofollow\"", "<img src=\"http://localhost:8081/upload/editor/2026/10/14/1_0.png\">");
    }
}
