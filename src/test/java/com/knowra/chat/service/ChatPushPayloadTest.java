package com.knowra.chat.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPushPayloadTest {

    private static ChatMsgSentEvent ev(String text, boolean img, boolean group, String name) {
        return new ChatMsgSentEvent(7L, 1L, text, img, group, name, Map.of(2L, false));
    }

    @Test
    void DM_은_보낸사람이_제목이고_누르면_그_방이_열린다() {
        Map<String, Object> p = ChatPushListener.payload(ev("안녕하세요", false, false, null), "deer", false);
        assertThat(p).containsEntry("title", "deer").containsEntry("body", "안녕하세요")
                .containsEntry("url", "/?chat=7").containsEntry("tag", "CHAT:7");
    }

    @Test
    void 그룹은_방이름이_제목이고_본문에_보낸사람() {
        Map<String, Object> p = ChatPushListener.payload(ev("내일 봬요", false, true, "독서 모임"), "deer", false);
        assertThat(p).containsEntry("title", "독서 모임").containsEntry("body", "deer: 내일 봬요");
    }

    @Test
    void 메시지_요청은_내용을_싣지_않는다() {
        Map<String, Object> p = ChatPushListener.payload(ev("비밀 이야기", false, false, null), "deer", true);
        assertThat(p.get("body").toString()).doesNotContain("비밀");
        assertThat(p).containsEntry("title", "메시지 요청");
    }

    @Test
    void 사진만_보내면_안내_문구_긴_글은_자른다() {
        assertThat(ChatPushListener.snippet("", true)).isEqualTo("사진을 보냈어요.");
        assertThat(ChatPushListener.snippet("줄\n바꿈", false)).isEqualTo("줄 바꿈");
        assertThat(ChatPushListener.snippet("가".repeat(100), false)).hasSize(ChatPushListener.SNIPPET + 1).endsWith("…");
    }
}
