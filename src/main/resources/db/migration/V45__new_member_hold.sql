-- ============================================================================
-- V45: 새 멤버 글 검토
--
-- 커뮤니티가 정한 날수(0 = 끔) 안에 들어온 멤버의 새 글은 HELD 로 둔다 — 운영진이 승인하면 그때 올라간다.
-- 목록·피드·검색은 stat='ACTIVE' 만 보므로 HELD 는 저절로 빠진다. 신고 자동 숨김(REVIEW)과 따로 둔다:
-- REVIEW 는 「문제없음」 이면 되돌리는데, 그 길로 검토 전 글이 올라가면 안 된다.
-- 「들어온 때」는 멤버 줄의 JOINED_AT — 처음 줄을 만든 때(FRST_CRT_DT)는 나갔다 다시 들어와도 옛날 그대로다.
-- ============================================================================
ALTER TABLE knowra_community.tbl_comm     ADD COLUMN IF NOT EXISTS NEW_MEMBER_HOLD_DAYS INT NOT NULL DEFAULT 0;
ALTER TABLE knowra_community.tbl_comm_mbr ADD COLUMN IF NOT EXISTS JOINED_AT TIMESTAMP DEFAULT NULL;
UPDATE knowra_community.tbl_comm_mbr SET JOINED_AT = FRST_CRT_DT WHERE STAT = 'ACTIVE' AND JOINED_AT IS NULL;
