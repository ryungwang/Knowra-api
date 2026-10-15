-- ============================================================================
-- V51: 사용자 언어·시간대
--
-- 알림·푸시·메일은 요청 없이(받는 사람에게) 나간다 — 그 사람의 언어와 시간대를 알아야 한다.
-- 지금까지의 사용자는 모두 한국어·서울로 둔다. 새로 가입하면 브라우저 언어(Accept-Language)·시간대로 정한다.
-- 언어: ko | en | ja | zh-Hans | zh-Hant (화면 src/i18n/index.js 와 같은 코드)
-- ============================================================================
ALTER TABLE knowra_user.tbl_user_stng ADD COLUMN IF NOT EXISTS LANG      VARCHAR(10) NOT NULL DEFAULT 'ko';
ALTER TABLE knowra_user.tbl_user_stng ADD COLUMN IF NOT EXISTS TIME_ZONE VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul';
COMMENT ON COLUMN knowra_user.tbl_user_stng.LANG      IS '알림·메일 언어 (ko / en / ja / zh-Hans / zh-Hant)';
COMMENT ON COLUMN knowra_user.tbl_user_stng.TIME_ZONE IS 'IANA 시간대 (예: Asia/Seoul, America/New_York) — 알림 속 날짜와 메일 보내는 시각';
