package com.knowra.user.listener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotifMessageTest {

    private final NotificationEventListener l = new NotificationEventListener(null, null, null, null, null, null);

    @Test
    void 한국어는_예전과_같은_문장() {
        assertThat(l.buildMessage("ko", null, "LIKE", "deer", null)).isEqualTo("deer님이 내 게시글을 좋아합니다.");
        assertThat(l.buildMessage("ko", null, "EVENT_SOON", null, "번개 모임")).isEqualTo("모임 「번개 모임」이 곧 시작해요.");
        assertThat(l.buildMessage("ko", null, "COMM_BANNED", "x", "독서|2026-10-18T23:25|도배"))
                .startsWith("독서에서 내보내졌어요. ").contains("까지 다시 참여할 수 없어요.").endsWith("사유: 도배");
        assertThat(l.buildMessage("ko", null, "COMM_BANNED", "x", "독서||")).isEqualTo("독서에서 내보내졌어요. 다시 참여할 수 없어요.");
    }

    @Test
    void 번역이_없는_언어도_문장이_깨지지_않는다() {
        // 표가 비어 있으면 원문(한국어)이 나오되, 조사 표시 「이(가)」가 그대로 남지 않아야 할 곳은 한국어뿐이다
        String en = l.buildMessage("en", null, "LIKE", "deer", null);
        assertThat(en).contains("deer");
    }
}
