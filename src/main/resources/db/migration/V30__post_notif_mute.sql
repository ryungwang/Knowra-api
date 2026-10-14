-- ============================================================================
-- V30: 글마다 알림 끄기
--
-- 내 글에 댓글이 몰리면 댓글 알림 전체를 꺼야 했다. 글 하나만 조용히 — 그 글의 댓글·답글·좋아요 알림(푸시 포함)을
-- 보내지 않는다. @멘션은 나를 직접 부른 것이라 그대로 보낸다.
-- ============================================================================
CREATE TABLE IF NOT EXISTS knowra_post.tbl_post_mute (
    USER_SN    BIGINT    NOT NULL,
    POST_SN    BIGINT    NOT NULL,
    CREATED_AT TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (USER_SN, POST_SN)
);
