-- ============================================================================
-- V65: 다른 언어 글 검색 — 검색어 번역을 기억해 둔다
--
-- 「등산」으로 찾으면 hiking·ハイキング·登山 으로도 찾는다. 검색어를 사이트 언어마다 한 번만 번역하고 여기 둔다 —
-- 같은 검색어를 다시 찾을 때 번역 업체를 부르지 않는다. text 가 빈 문자열이면 그 언어와 같았다(번역할 것 없음).
-- 30일 지난 것은 매일 지운다(TranslationService.purge).
-- ============================================================================
CREATE TABLE IF NOT EXISTS knowra_com.tbl_query_translation
(
    q          VARCHAR(100) NOT NULL,   -- 소문자·공백 하나로 정리한 검색어
    lang       VARCHAR(10)  NOT NULL,
    text       VARCHAR(400) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (q, lang)
);
