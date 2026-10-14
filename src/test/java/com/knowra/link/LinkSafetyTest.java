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

    @Test
    void 닫히지_않은_meta_가_가득한_페이지도_금방_끝난다() {
        // 예전 정규식은 이 입력에서 패턴 하나에 수십 초(제곱으로 늘어남)가 걸렸다
        String evil = "<title>t</title>" + "<meta ".repeat(33_000);
        long t = System.nanoTime();
        LinkPreviewService.parse(evil, "https://example.com/", "https://example.com/");
        assertThat((System.nanoTime() - t) / 1_000_000).isLessThan(2_000);
    }

    @Test
    void 속성_순서와_엔티티가_달라도_읽는다() {
        String html = "<html><head><meta content=\"설명 &amp; 요약\" name=\"description\">"
                + "<meta property=\"og:title\" content=\"제목 &quot;A&quot;\"></head></html>";
        Map<String, Object> m = LinkPreviewService.parse(html, "https://example.com/", "https://example.com/");
        assertThat(m.get("title")).isEqualTo("제목 \"A\"");
        assertThat(m.get("description")).isEqualTo("설명 & 요약");
    }

    @Test
    void IPv4_를_품은_IPv6_도_안의_주소로_본다() throws Exception {
        assertThat(SafeFetcher.isPublic(java.net.InetAddress.getByName("64:ff9b::a9fe:a9fe"))).isFalse();   // NAT64 → 169.254.169.254
        assertThat(SafeFetcher.isPublic(java.net.InetAddress.getByName("2002:0a00:0001::1"))).isFalse();    // 6to4 → 10.0.0.1
        assertThat(SafeFetcher.isPublic(java.net.InetAddress.getByName("2002:0808:0808::1"))).isTrue();     // 6to4 → 8.8.8.8
    }
}
