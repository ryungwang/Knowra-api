-- ============================================================================
-- V61: 커뮤니티 언어와 글 언어
--
-- tbl_comm.LANG: 커뮤니티의 주 언어(ko·en·ja·zh-Hans·es …). NULL = 여러 언어. 운영진이 정하고, 탐색에서 언어로 거른다.
-- tbl_post.LANG: 글의 언어 — 저장할 때 글자 체계로 가린다(LangDetect): ko·ja·zh, 로마자는 latn(영어·스페인어 등을 글자로는 못 가른다).
--   화면이 보는 사람 언어와 다르면 「번역」 단추를 띄운다(브라우저 내장 번역기 — 서버로 글을 보내지 않는다).
-- ============================================================================
ALTER TABLE knowra_community.tbl_comm ADD COLUMN IF NOT EXISTS LANG VARCHAR(10) DEFAULT NULL;
ALTER TABLE knowra_post.tbl_post ADD COLUMN IF NOT EXISTS LANG VARCHAR(10) DEFAULT NULL;
COMMENT ON COLUMN knowra_community.tbl_comm.LANG IS '커뮤니티 주 언어 (NULL = 여러 언어)';
COMMENT ON COLUMN knowra_post.tbl_post.LANG IS '글 언어 — ko·ja·zh·latn(로마자, 세부 언어 미상)·NULL(판단 못 함)';
CREATE INDEX IF NOT EXISTS idx_comm_lang ON knowra_community.tbl_comm (LANG) WHERE LANG IS NOT NULL;
