-- ============================================================================
-- V20: 예약 게시
--
-- 예약 글은 STAT='SCHEDULED' 로 저장한다 — 공개 조건(STAT='ACTIVE')에 안 걸리므로 피드·검색·목록 어디에도
-- 따로 손대지 않고 숨는다. RSRV_AT 이 되면 스케줄러가 ACTIVE 로 바꾸고 FRST_CRT_DT 를 그 시각으로 옮긴다
-- (예약을 건 시각으로 두면 최신 피드에서 묻힌다). 알림·멘션·뱃지도 그때 나간다.
-- ============================================================================
ALTER TABLE knowra_post.tbl_post ADD COLUMN IF NOT EXISTS RSRV_AT TIMESTAMP(6) DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_post_rsrv ON knowra_post.tbl_post (STAT, RSRV_AT);
