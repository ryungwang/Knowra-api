-- ============================================================================
-- V64: 글·댓글 자동 번역
--
-- 다른 언어로 쓴 글을 읽는 사람 언어로 보여 준다. 번역은 (대상, 언어)마다 한 번만 하고 여기 둔다 —
-- 같은 글을 일본어 사용자 100명이 열어도 번역 업체 호출은 한 번이다. 원문이 바뀌면(src_hash) 다시 한다.
--   same = 원문이 이미 그 언어였다(로마자 글이 영어였던 경우 등) — 다시 묻지 않게 기억만 한다
-- tbl_translate_usage: 달마다 보낸 글자 수 — 무료 한도를 넘지 않게 막는다(TranslateQuota)
-- ============================================================================
CREATE TABLE IF NOT EXISTS knowra_com.tbl_translation
(
    target_kind VARCHAR(10) NOT NULL,
    target_sn   BIGINT      NOT NULL,
    lang        VARCHAR(10) NOT NULL,
    src_hash    CHAR(40)    NOT NULL,
    src_lang    VARCHAR(10),
    same        BOOLEAN     NOT NULL DEFAULT FALSE,
    title       TEXT,
    body        TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (target_kind, target_sn, lang)
);

CREATE TABLE IF NOT EXISTS knowra_com.tbl_translate_usage
(
    month CHAR(7) PRIMARY KEY,   -- YYYY-MM (UTC)
    chars BIGINT  NOT NULL DEFAULT 0
);
