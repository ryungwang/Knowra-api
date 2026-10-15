-- ============================================================================
-- V49: 커뮤니티 기간 강퇴와 사유
--
-- 강퇴(BANNED)는 영구뿐이었고, 당사자는 이유를 몰랐다. 기간(끝나면 스스로 다시 참여할 수 있다)과 사유를 둔다.
-- BAN_UNTIL 이 NULL 이면 영구. 사유는 알림으로 당사자에게 가고, 운영진 「내보낸 사람」 목록에 보인다.
-- ============================================================================
ALTER TABLE knowra_community.tbl_comm_mbr ADD COLUMN IF NOT EXISTS BAN_UNTIL  TIMESTAMP;
ALTER TABLE knowra_community.tbl_comm_mbr ADD COLUMN IF NOT EXISTS BAN_REASON VARCHAR(300);
COMMENT ON COLUMN knowra_community.tbl_comm_mbr.BAN_UNTIL  IS '강퇴가 풀리는 시각 (NULL = 영구, BANNED 일 때만 뜻이 있다)';
COMMENT ON COLUMN knowra_community.tbl_comm_mbr.BAN_REASON IS '강퇴 사유 — 당사자에게 알린다';
