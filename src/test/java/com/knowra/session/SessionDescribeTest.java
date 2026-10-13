package com.knowra.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionDescribeTest {
    @Test
    void 기기_이름() {
        assertThat(SessionService.describe("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36")).isEqualTo("Chrome · macOS");
        assertThat(SessionService.describe("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1")).isEqualTo("Safari · iPhone");
        assertThat(SessionService.describe("Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 SamsungBrowser/25.0 Chrome/121 Mobile Safari/537.36")).isEqualTo("삼성 인터넷 · Android");
        assertThat(SessionService.describe(null)).isEqualTo("알 수 없는 기기");
    }

    @Test
    void IP_는_대역만() {
        assertThat(SessionService.maskIp("211.36.142.7")).isEqualTo("211.36.*.*");
        assertThat(SessionService.maskIp("2001:db8:1::5")).isEqualTo("2001:db8:…");
    }
}
