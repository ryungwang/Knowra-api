-- ============================================================================
-- V32: 그룹 채팅
--
-- 1:1 DM 뿐이라 모임 참석자끼리 이야기할 곳이 없었다. ROOM_TYP='GROUP' 방에 이름을 단다.
-- 초대받은 사람은 DM 처럼 「요청」으로 받는다(모르는 사람이 남을 아무 방에나 넣지 못하게) — 모임 대화방만 예외로
-- 참석을 누른 사람을 바로 넣는다(스스로 고른 모임이다). EVENT_SN 이 있으면 그 모임의 대화방이고 모임당 하나.
-- 멤버 STAT 에 LEFT(나감)가 생긴다.
-- ============================================================================
ALTER TABLE knowra_user.tbl_chat_room ADD COLUMN IF NOT EXISTS NAME VARCHAR(50) DEFAULT NULL;
ALTER TABLE knowra_user.tbl_chat_room ADD COLUMN IF NOT EXISTS CREATED_BY BIGINT DEFAULT NULL;
ALTER TABLE knowra_user.tbl_chat_room ADD COLUMN IF NOT EXISTS EVENT_SN BIGINT DEFAULT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_room_event ON knowra_user.tbl_chat_room (EVENT_SN) WHERE EVENT_SN IS NOT NULL;
