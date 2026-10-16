-- ============================================================================
-- V55: 태그 팔로우
--
-- 사람·커뮤니티 말고 주제(태그)를 따라간다 — 「팔로잉」 피드에 그 태그의 새 글이 들어오고, 추천 피드의 관심 점수도 오른다.
-- 「내 태그」(tbl_user_tag)는 내가 글에 붙인 태그의 기록이라 뜻이 다르다. 팔로우는 이 표에만 둔다.
-- ============================================================================
CREATE TABLE IF NOT EXISTS knowra_user.tbl_tag_follow
(
    USER_SN    BIGINT NOT NULL,
    TAG_SN     BIGINT NOT NULL,
    CREATED_AT TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (USER_SN, TAG_SN),
    CONSTRAINT FK_TAG_FOLLOW_USER FOREIGN KEY (USER_SN) REFERENCES knowra_user.tbl_user (USER_SN) ON DELETE CASCADE,
    CONSTRAINT FK_TAG_FOLLOW_TAG  FOREIGN KEY (TAG_SN)  REFERENCES knowra_com.tbl_tag (TAG_SN) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_tag_follow_tag ON knowra_user.tbl_tag_follow (TAG_SN);
COMMENT ON TABLE knowra_user.tbl_tag_follow IS '태그 팔로우 — 팔로잉 피드에 그 태그의 글을 넣는다';
