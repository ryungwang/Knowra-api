-- ============================================================================
-- V43: 연재 구독 — 새 편이 올라오면 알린다
-- 연재를 지우면 구독도 지운다(SeriesService.remove).
-- ============================================================================
CREATE TABLE IF NOT EXISTS knowra_post.tbl_series_sub (
    SERIES_SN  BIGINT    NOT NULL,
    USER_SN    BIGINT    NOT NULL,
    CREATED_AT TIMESTAMP NOT NULL,
    PRIMARY KEY (SERIES_SN, USER_SN)
);
CREATE INDEX IF NOT EXISTS idx_series_sub_user ON knowra_post.tbl_series_sub (USER_SN);
