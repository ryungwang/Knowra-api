-- ============================================================================
-- V52: 커뮤니티 닫기 · 댓글 잠금
--
-- CLOSED_AT: 주인이 커뮤니티를 닫은 시각(NULL = 열림). 닫히면 읽기 전용이다 — 글은 그대로 읽히고,
--   새 글·댓글·가입은 막힌다. 운영진은 안내 글·댓글을 남길 수 있다. 주인이 다시 열 수 있다.
--   지우기와 다르다: 지우면 글이 사라지지만, 닫으면 쌓인 글이 기록으로 남는다.
-- CMT_LOCKED_AT / CMT_LOCK_BY: 글의 새 댓글을 막은 시각과 누가 막았는지.
--   AUTHOR = 글쓴이가 스스로, MOD = 운영진. 운영진이 잠근 건 글쓴이가 풀 수 없다(싸움을 말린 걸 당사자가 되돌리면 안 된다).
-- ============================================================================
ALTER TABLE knowra_community.tbl_comm ADD COLUMN IF NOT EXISTS CLOSED_AT TIMESTAMP(6) DEFAULT NULL;
COMMENT ON COLUMN knowra_community.tbl_comm.CLOSED_AT IS '닫힌 시각 (NULL = 열림). 닫히면 새 글·댓글·가입이 막힌다';

ALTER TABLE knowra_post.tbl_post ADD COLUMN IF NOT EXISTS CMT_LOCKED_AT TIMESTAMP(6) DEFAULT NULL;
ALTER TABLE knowra_post.tbl_post ADD COLUMN IF NOT EXISTS CMT_LOCK_BY   VARCHAR(10)  DEFAULT NULL;
COMMENT ON COLUMN knowra_post.tbl_post.CMT_LOCKED_AT IS '댓글을 잠근 시각 (NULL = 열림)';
COMMENT ON COLUMN knowra_post.tbl_post.CMT_LOCK_BY   IS 'AUTHOR = 글쓴이가 잠금, MOD = 운영진이 잠금(글쓴이가 못 푼다)';
