-- ============================================================================
-- V22: 파일 저장소 키
--
-- 업로드를 StorageService(prod=S3, 그 밖=로컬 디스크)로 옮기며 객체 키를 남긴다. 키가 있으면 공개 URL 은
-- 저장소가 만든다(TblComFile.getUrl). 키가 없는 줄은 예전 /upload 파일이다 — LegacyUploadMigrator 가 옮기며 채운다.
-- ============================================================================
ALTER TABLE knowra_com.tbl_com_file ADD COLUMN IF NOT EXISTS FILE_KEY VARCHAR(300) DEFAULT NULL;
ALTER TABLE knowra_com.tbl_com_file ALTER COLUMN ATCH_FILE_PATH_NM TYPE VARCHAR(300);
