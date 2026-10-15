-- ============================================================================
-- V50: 회원 탈퇴 유예(14일)
--
-- 탈퇴는 누르는 즉시 글·댓글·개인정보를 지웠다 — 실수로 눌렀거나 계정을 빼앗긴 사람이 되돌릴 길이 없었다.
-- 이제 신청 시각만 남기고 모든 기기에서 로그아웃시킨다. 14일 안에 다시 로그인하면 취소되고, 지나면 매일 새벽
-- AccountDeletionService.purgeDue 가 예전과 같은 방식으로 지운다.
-- ============================================================================
ALTER TABLE knowra_user.tbl_user ADD COLUMN IF NOT EXISTS DELETE_REQ_AT TIMESTAMP DEFAULT NULL;
COMMENT ON COLUMN knowra_user.tbl_user.DELETE_REQ_AT IS '탈퇴 신청 시각 — 14일 뒤 지운다. 그 전에 로그인하면 NULL 로 돌아간다';
CREATE INDEX IF NOT EXISTS idx_user_delete_req ON knowra_user.tbl_user (DELETE_REQ_AT) WHERE DELETE_REQ_AT IS NOT NULL;
