package com.knowra.share;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpoilerPreviewTest {
    @Test
    void 가린_부분은_미리보기에_나오지_않는다() {
        assertThat(ShareController.hideSpoilers("범인은 ||집사|| 였다. 그리고 ||결말은 해피엔딩||"))
                .isEqualTo("범인은 ▒▒▒ 였다. 그리고 ▒▒▒");
        assertThat(ShareController.hideSpoilers("a || b")).isEqualTo("a || b");   // 짝이 안 맞으면 그대로
    }
}
