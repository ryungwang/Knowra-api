-- ============================================================================
-- V63: 글 자동 검수(AI)
--
-- 운영진이 모든 언어를 읽을 수 없어서, 새 글·댓글·수정본을 기계가 먼저 보고 해로우면 신고함에 올린다.
-- 막지 않는다 — 사람이 신고함에서 판단한다(아동 대상 성적 내용만 아동 안전 신고처럼 바로 가린다).
--   tbl_report.reporter_sn NULL = 자동 검수가 올린 신고(신고 누적 문턱에는 세지 않는다)
--   tbl_auto_mod        : 무엇을 어떤 내용으로 봤는지 — 같은 내용은 다시 보내지 않는다
--   tbl_auto_mod_cursor : 어디까지 훑었는지(글·댓글 따로). 처음 켤 때의 시각부터 — 예전 글은 보내지 않는다
-- ============================================================================
ALTER TABLE knowra_user.tbl_report ALTER COLUMN reporter_sn DROP NOT NULL;
COMMENT ON COLUMN knowra_user.tbl_report.reporter_sn IS '신고한 회원. NULL = 자동 검수(AI)가 올린 신고';

CREATE TABLE IF NOT EXISTS knowra_com.tbl_auto_mod
(
    target_kind VARCHAR(10) NOT NULL,
    target_sn   BIGINT      NOT NULL,
    text_hash   CHAR(40)    NOT NULL,
    flagged     BOOLEAN     NOT NULL,
    checked_at  TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (target_kind, target_sn)
);

CREATE TABLE IF NOT EXISTS knowra_com.tbl_auto_mod_cursor
(
    kind VARCHAR(10) PRIMARY KEY,
    upto TIMESTAMP   NOT NULL
);
