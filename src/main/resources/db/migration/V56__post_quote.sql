-- ============================================================================
-- V56: 글 인용
--
-- QUOTE_POST_SN: 이 글이 인용한 글. 인용한 글 안에 원글이 작은 카드로 보인다(보는 사람이 원글을 볼 수 있을 때만 —
-- 비공개 커뮤니티 글·지워진 글은 「볼 수 없는 글」로). 원글이 지워져도 인용 글은 남는다(ON DELETE SET NULL 은 쓰지 않는다:
-- 글은 actvtn_yn 으로만 지우므로 행이 사라지지 않는다).
-- ============================================================================
ALTER TABLE knowra_post.tbl_post ADD COLUMN IF NOT EXISTS QUOTE_POST_SN BIGINT DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_post_quote ON knowra_post.tbl_post (QUOTE_POST_SN) WHERE QUOTE_POST_SN IS NOT NULL;
COMMENT ON COLUMN knowra_post.tbl_post.QUOTE_POST_SN IS '인용한 글 (NULL = 인용 없음)';
