-- ============================================================================
-- V69: 신고 큐를 언어로 나눈다
--
-- tbl_report.LANG: 신고된 대상의 언어 — 글자 체계로 가린 ko·ja·zh·latn(로마자), 판단 못 하면 und.
--   글은 tbl_post.LANG 을 그대로 쓰고, 댓글·메시지는 본문으로, 사용자 신고는 그 사람의 화면 언어로 정한다.
--   이 마이그레이션 이전 신고는 서버가 뜰 때 채운다(ReportLangBackfill).
-- tbl_user.MOD_LANGS: 검수자가 맡는 언어(쉼표로 이음). NULL = 전부. 신고 큐의 기본 거름이다.
--   아동 안전 신고와 und 는 거름과 상관없이 모두에게 보인다 — 아무도 안 보는 신고가 생기면 안 된다.
-- ============================================================================
ALTER TABLE knowra_user.tbl_report ADD COLUMN IF NOT EXISTS LANG VARCHAR(10) DEFAULT NULL;
ALTER TABLE knowra_user.tbl_user ADD COLUMN IF NOT EXISTS MOD_LANGS VARCHAR(60) DEFAULT NULL;
UPDATE knowra_user.tbl_report r SET lang = COALESCE(p.lang, 'und')
  FROM knowra_post.tbl_post p WHERE r.target_kind = 'POST' AND p.post_sn = r.target_sn AND r.lang IS NULL;
CREATE INDEX IF NOT EXISTS ix_tbl_report_stat_lang ON knowra_user.tbl_report (stat, lang);
COMMENT ON COLUMN knowra_user.tbl_report.LANG IS '신고 대상 언어 — ko·ja·zh·latn·und';
COMMENT ON COLUMN knowra_user.tbl_user.MOD_LANGS IS '검수자가 맡는 언어(쉼표) — NULL = 전부';
