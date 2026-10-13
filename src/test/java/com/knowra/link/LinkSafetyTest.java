package com.knowra.link;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 링크 미리보기 — SSRF 거절과 OG 파싱. 네트워크 없이 도는 것만. */
class LinkSafetyTest {

    @Test
    void 내부_주소와_이상한_URL_은_거절() {
        for (String bad : new String[]{
                "http://127.0.0.1/", "http://localhost:8081/api/admin", "http://10.0.0.5/", "http://192.168.0.1/",
                "http://169.254.169.254/latest/meta-data/", "http://[::1]/", "http://0.0.0.0/", "http://100.64.0.1/",
                "file:///etc/passwd", "ftp://example.com/", "http://example.com:8080/", "http://user:pw@example.com/",
                "http://[::ffff:127.0.0.1]/", "http://[fd00::1]/"}) {
            assertThatThrownBy(() -> SafeFetcher.check(bad)).as(bad).isInstanceOf(Exception.class);
        }
    }

    @Test
    void 공인_IP_리터럴은_통과() throws Exception {
        assertThat(SafeFetcher.check("https://1.1.1.1/").getHost()).isEqualTo("1.1.1.1");
    }

    @Test
    void OG_를_읽고_상대_이미지를_절대로() {
        String html = "<html><head><title>기본 제목</title>"
                + "<meta content=\"설명 &amp; 요약\" property=\"og:description\">"
                + "<meta property='og:title' content='OG 제목'>"
                + "<meta property=\"og:image\" content=\"/img/card.png\">"
                + "</head></html>";
        Map<String, Object> m = LinkPreviewService.parse(html, "https://www.example.com/a/b", "https://example.com/x");
        assertThat(m.get("title")).isEqualTo("OG 제목");
        assertThat(m.get("description")).isEqualTo("설명 & 요약");
        assertThat(m.get("image")).isEqualTo("https://www.example.com/img/card.png");
        assertThat(m.get("siteName")).isEqualTo("example.com");
    }

    @Test
    void 제목이_없으면_카드를_만들지_않는다() {
        assertThat(LinkPreviewService.parse("<html><body>hi</body></html>", "https://e.com/", "https://e.com/")).isNull();
    }
}
