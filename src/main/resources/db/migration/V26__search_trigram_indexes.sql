-- ============================================================================
-- V26: 검색 인덱스(트라이그램)
--
-- 검색은 QueryDSL containsIgnoreCase = 「lower(컬럼) LIKE '%단어%'」 다. 앞에 % 가 붙어 보통 인덱스를 못 타고
-- 글이 늘면 매번 표 전체를 읽는다. pg_trgm GIN 인덱스는 이 모양을 그대로 받는다 — 인덱스 식이 쿼리와 똑같이
-- lower(컬럼) 이어야 탄다(쿼리를 바꾸면 여기도 같이 바꿀 것).
--
-- ⚠️ pg_trgm 은 「신뢰된 확장」이라 DB 주인이면 만들 수 있다(PostgreSQL 13+). 권한이 없으면 인덱스를 건너뛴다 —
-- 검색은 느릴 뿐 그대로 돈다. 그 경우 DB 관리자가 CREATE EXTENSION pg_trgm 을 한 뒤 아래 CREATE INDEX 를 손으로.
-- ============================================================================
DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS pg_trgm;
    EXCEPTION WHEN insufficient_privilege OR feature_not_supported OR undefined_file THEN
        RAISE WARNING 'V26: pg_trgm 을 만들 수 없어 검색 인덱스를 건너뛴다 (%)', SQLERRM;
        RETURN;
    END;

    CREATE INDEX IF NOT EXISTS idx_trgm_post_ttl     ON knowra_post.tbl_post        USING gin (lower(post_ttl) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_trgm_post_cntnt   ON knowra_post.tbl_post        USING gin (lower(post_cntnt) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_trgm_tag_nm       ON knowra_com.tbl_tag          USING gin (lower(tag_nm) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_trgm_comm_dspl_nm ON knowra_community.tbl_comm   USING gin (lower(comm_dspl_nm) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_trgm_comm_nm      ON knowra_community.tbl_comm   USING gin (lower(comm_nm) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_trgm_comm_desc    ON knowra_community.tbl_comm   USING gin (lower(comm_desc) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_trgm_user_nick    ON knowra_user.tbl_user        USING gin (lower(nick_name) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_trgm_user_name    ON knowra_user.tbl_user        USING gin (lower(name) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_trgm_user_bio     ON knowra_user.tbl_user        USING gin (lower(bio) gin_trgm_ops);
END $$;
