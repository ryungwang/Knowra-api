package com.knowra.cmm.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitRulesTest {

    /** 쓰기 창구가 표에서 빠지면 도배에 무방비다 — 경로가 정확히 같아야 걸리므로 이름을 못 박는다 */
    @Test
    void 알림을_일으키거나_행을_만드는_쓰기는_제한이_있다() {
        List<String> writes = List.of(
                "/api/post/setPostCmtEdit", "/api/community/setMember", "/api/community/updateCommunity",
                "/api/chat/respondRequest", "/api/poll/vote", "/api/safety/block", "/api/safety/mute",
                "/api/keyword/add", "/api/saveFolder/create", "/api/series/create", "/api/draft/save",
                "/api/chat/sendMsg", "/api/post/setPostCmt", "/api/safety/report");
        assertThat(RateLimitInterceptor.RULES.keySet()).containsAll(writes);
    }
}
