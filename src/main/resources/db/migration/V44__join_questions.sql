-- ============================================================================
-- V44: 가입 질문
--
-- 승인제(제한적·비공개) 커뮤니티가 가입 신청 때 묻는 질문(최대 3개)과 신청자의 답.
-- 답은 질문 글까지 함께 남긴다 — 신청 뒤에 운영진이 질문을 고쳐도 무엇에 답했는지 남는다.
-- 승인·거절이 끝나면 답은 지운다(더 쓸 데가 없는 개인 정보).
-- ============================================================================
CREATE TABLE IF NOT EXISTS knowra_community.tbl_comm_join_q (
    COMM_SN  BIGINT       NOT NULL,
    ORD      INT          NOT NULL,
    QUESTION VARCHAR(100) NOT NULL,
    PRIMARY KEY (COMM_SN, ORD)
);
CREATE TABLE IF NOT EXISTS knowra_community.tbl_comm_join_ans (
    COMM_SN    BIGINT       NOT NULL,
    USER_SN    BIGINT       NOT NULL,
    ORD        INT          NOT NULL,
    QUESTION   VARCHAR(100) NOT NULL,
    ANSWER     VARCHAR(500) NOT NULL,
    CREATED_AT TIMESTAMP    NOT NULL,
    PRIMARY KEY (COMM_SN, USER_SN, ORD)
);
