-- ============================================================================
-- V7: 댓글 사진 한 장. 파일은 글 편집기와 같은 업로드(/api/common/uploadImage → /upload/editor/...)를
-- 쓰고, 여기엔 그 경로만 둔다. 서버는 이 서버가 만든 업로드 경로 모양일 때만 받는다(PostService).
-- ============================================================================
ALTER TABLE knowra_post.tbl_post_cmt ADD COLUMN IF NOT EXISTS IMG_URL VARCHAR(300) DEFAULT NULL;
