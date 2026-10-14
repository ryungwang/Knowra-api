-- ============================================================================
-- V40: 휴면 계정
--
-- 1년 동안 들어오지 않은 계정은 휴면(STATUS='D')으로 바꾼다 — 로그인 상태를 끊고, 메일·푸시를 멈추고,
-- 사람 찾기·멘션 추천에서 뺀다(이들은 원래 STATUS='Y' 만 본다). 글·댓글은 그대로 남는다.
-- 휴면 30일 전에 한 번 메일로 알린다. 로그인하면 바로 풀린다.
-- 「마지막 접속」은 로그인 기록과 기기 세션의 마지막 사용 중 늦은 쪽 — 자동 로그인으로 쓰는 사람은 로그인 기록이 없다.
-- ============================================================================
ALTER TABLE knowra_user.tbl_user ADD COLUMN IF NOT EXISTS DORMANT_AT        TIMESTAMP DEFAULT NULL;
ALTER TABLE knowra_user.tbl_user ADD COLUMN IF NOT EXISTS DORMANT_NOTICE_AT TIMESTAMP DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_lgn_hstry_user_dt ON knowra_user.tbl_user_lgn_hstry (USER_SN, LGN_DT);
