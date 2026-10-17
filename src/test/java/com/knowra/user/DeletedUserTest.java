package com.knowra.user;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeletedUserTest {

    @Test
    void 응답_안의_탈퇴_이름을_그_언어로() {
        Map<String, Object> peer = new LinkedHashMap<>(Map.of("nickName", "탈퇴회원58", "name", "탈퇴한 사용자", "userSn", 58));
        Map<String, Object> other = new LinkedHashMap<>(Map.of("nickName", "탈퇴회원장인", "name", "민수"));
        Map<String, Object> result = new LinkedHashMap<>(Map.of("list", new ArrayList<>(List.of(Map.of("peer", peer), other))));
        DeletedUser.localize(result, "en");
        assertThat(peer).containsEntry("nickName", "deleted58").containsEntry("name", "Deleted user").containsEntry("userSn", 58);
        assertThat(other).as("숫자가 아닌 건 사람이 지은 이름").containsEntry("nickName", "탈퇴회원장인").containsEntry("name", "민수");
    }

    @Test
    void 한국어면_그대로_바꿀_수_없는_지도는_건너뛴다() {
        Map<String, Object> peer = new LinkedHashMap<>(Map.of("name", "탈퇴한 사용자"));
        DeletedUser.localize(peer, "ko");
        assertThat(peer).containsEntry("name", "탈퇴한 사용자");
        DeletedUser.localize(Map.of("name", "탈퇴한 사용자"), "ja");   // 예외 없이 지나간다
    }

    @Test
    void 탈퇴한_사람처럼_보이는_닉네임은_고를_수_없다() {
        assertThat(UserRules.nickNameError("탈퇴회원5")).isNotNull();
        assertThat(UserRules.nickNameError("deleted_user12")).isNotNull();
        assertThat(UserRules.nickNameError("Deleted58")).isNotNull();
        assertThat(UserRules.nickNameError("退会ユーザー3")).isNotNull();
        assertThat(UserRules.nickNameError("deletedream")).as("평범한 이름은 된다").isNull();
    }
}
