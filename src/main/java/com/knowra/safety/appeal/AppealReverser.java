package com.knowra.safety.appeal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 이의가 받아들여졌을 때 조치를 되돌린다. 글은 actvtn_yn 으로, 댓글은 stat 으로 지웠으므로(PostService) 그 값을 되돌린다.
 * 피드·목록 캐시는 다시 계산될 때 들어온다(즉시 보이지 않을 수 있다).
 */
@Component
@RequiredArgsConstructor
class AppealReverser {

    private final JdbcTemplate jdbc;

    void reverse(String kind, Long targetSn, long userSn, long admin) {
        switch (kind) {
            case "POST_REMOVE" -> jdbc.update("UPDATE knowra_post.tbl_post SET actvtn_yn = 'Y', mdfr_sn = ? WHERE post_sn = ?", admin, targetSn);
            case "CMT_REMOVE" -> {
                int n = jdbc.update("UPDATE knowra_post.tbl_post_cmt SET stat = 'ACTIVE', actvtn_yn = 'Y', mdfr_sn = ? WHERE post_cmt_sn = ? AND stat = 'DELETED'", admin, targetSn);
                if (n > 0) jdbc.update("UPDATE knowra_post.tbl_post p SET cmt_cnt = p.cmt_cnt + 1 FROM knowra_post.tbl_post_cmt c WHERE c.post_cmt_sn = ? AND p.post_sn = c.post_sn", targetSn);
            }
            case "MSG_HIDE" -> jdbc.update("UPDATE knowra_user.tbl_chat_msg SET hidden_at = NULL WHERE msg_sn = ?", targetSn);
            case "SUSPEND" -> jdbc.update("UPDATE knowra_user.tbl_user SET status = 'Y', suspended_until = NULL, suspend_reason = NULL WHERE user_sn = ? AND status = 'S'", userSn);
            default -> { }
        }
    }
}
