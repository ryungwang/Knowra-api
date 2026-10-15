-- ============================================================================
-- V48: DM·그룹 메시지 푸시 알림 설정
--
-- 메시지는 접속 중인 소켓에만 실시간으로 갔다 — 창을 닫아 둔 사람은 새 DM 을 몰랐다. 이제 접속하지 않은
-- 사람에게 웹 푸시를 보내고, 끌 수 있게 설정 칸을 둔다. 기본은 켜짐(DM 은 나를 향한 말이다).
-- ============================================================================
ALTER TABLE knowra_user.tbl_user_stng ADD COLUMN IF NOT EXISTS MSG_NTFCTN_YN VARCHAR(1) NOT NULL DEFAULT 'Y';
COMMENT ON COLUMN knowra_user.tbl_user_stng.MSG_NTFCTN_YN IS '메시지(DM·그룹) 푸시 알림 여부 (Y / N)';
