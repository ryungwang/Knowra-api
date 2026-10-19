-- ============================================================================
-- V66: 사이트 운영 역할을 DB 에 둔다
--
-- 지금까지는 「1번 회원 = 관리자」가 코드(JwtProvider)에 박혀 있어 운영자를 늘릴 수 없었다.
--   USER  — 보통 회원
--   MOD   — 검수자: 신고·이의 제기 처리, 보통 회원 정지
--   ADMIN — 관리자: 전부 + 역할 지정·공지·금칙어·오류·저작권
-- 토큰의 role 클레임이 이 값을 싣는다. 내리면 그 사람의 세션을 끊어 바로 반영한다.
-- ============================================================================
ALTER TABLE knowra_user.tbl_user ADD COLUMN site_role VARCHAR(10) NOT NULL DEFAULT 'USER';
ALTER TABLE knowra_user.tbl_user ADD CONSTRAINT ck_tbl_user_site_role CHECK (site_role IN ('USER', 'MOD', 'ADMIN'));
UPDATE knowra_user.tbl_user SET site_role = 'ADMIN' WHERE user_sn = 1;   -- 지금까지의 관리자
CREATE INDEX ix_tbl_user_site_role ON knowra_user.tbl_user (site_role) WHERE site_role <> 'USER';
