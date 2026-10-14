-- ============================================================================
-- V23: 기간 정지
--
-- 정지(status='S')는 켜고 끄는 것뿐이라 풀어 줄 때까지 영구 정지였고, 본인은 이유도 몰랐다.
-- SUSPENDED_UNTIL 이 있으면 그때 저절로 풀린다(NULL = 무기한). SUSPEND_REASON 은 로그인 화면에 보인다.
-- ============================================================================
ALTER TABLE knowra_user.tbl_user ADD COLUMN IF NOT EXISTS SUSPENDED_UNTIL TIMESTAMP DEFAULT NULL;
ALTER TABLE knowra_user.tbl_user ADD COLUMN IF NOT EXISTS SUSPEND_REASON VARCHAR(200) DEFAULT NULL;
