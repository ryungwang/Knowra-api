-- ============================================================
--  Knowra — 커뮤니티 DDL
--  DB: MySQL 8.x  /  Schema: knowra_community
--  규칙: DDL_규칙.md 참고
-- ============================================================

CREATE TABLE knowra_community.tbl_comm (
    COMM_SN         BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '커뮤니티 SN (PK)',
    COMM_NM         VARCHAR(320)    NOT NULL UNIQUE          COMMENT '슬러그 (URL 식별자)',
    COMM_DSPL_NM    VARCHAR(100)    NULL                     COMMENT '표시 이름',
    COMM_DESC       VARCHAR(320)    NULL                     COMMENT '설명',
    CTGR_SN         BIGINT          NOT NULL                 COMMENT '카테고리 SN (tbl_ctgr FK)',
    PRVCY_STNG      VARCHAR(20)     NOT NULL DEFAULT 'public' COMMENT 'public / restricted / anonymous / private',
    LOGO_FILE_SN    BIGINT          NULL                     COMMENT '로고 이미지 (TBL_COM_FILE FK)',
    BNR_FILE_SN     BIGINT          NULL                     COMMENT '배너 이미지 (TBL_COM_FILE FK)',
    STAT            CHAR(1)         NOT NULL DEFAULT 'Y'     COMMENT 'Y / N',
    ACTVTN_YN       CHAR(1)         NOT NULL DEFAULT 'Y'     COMMENT '활성화 여부',
    CREATR_SN       BIGINT          NOT NULL                 COMMENT '생성자 SN',
    FRST_CRT_DT     DATETIME        NOT NULL DEFAULT NOW()   COMMENT '최초 생성일시',
    MDFR_SN         BIGINT          NULL                     COMMENT '수정자 SN',
    MDFCN_DT        DATETIME        NULL ON UPDATE NOW()     COMMENT '수정일시',
    PRIMARY KEY (COMM_SN)
) COMMENT '커뮤니티';

CREATE TABLE knowra_community.tbl_comm_mbr (
    MBR_SN      BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '멤버 SN (PK)',
    COMM_SN     BIGINT      NOT NULL                 COMMENT '커뮤니티 SN (tbl_comm FK)',
    USER_SN     BIGINT      NOT NULL                 COMMENT '사용자 SN (TBL_USER FK)',
    ROLE        VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER / ADMIN / MEMBER',
    JOIN_TYP    VARCHAR(20) NOT NULL DEFAULT 'APPLY'  COMMENT 'APPLY / INVITE / AUTO — 백엔드가 PRVCY_STNG 보고 결정',
    STAT        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'PENDING / ACTIVE / REJECTED / BANNED / WITHDRAWN',
    ACTVTN_YN   CHAR(1)     NOT NULL DEFAULT 'Y'     COMMENT '활성화 여부',
    CREATR_SN   BIGINT      NOT NULL                 COMMENT '생성자 SN',
    FRST_CRT_DT DATETIME    NOT NULL DEFAULT NOW()   COMMENT '최초 생성일시',
    MDFR_SN     BIGINT      NULL                     COMMENT '수정자 SN',
    MDFCN_DT    DATETIME    NULL ON UPDATE NOW()     COMMENT '수정일시',
    PRIMARY KEY (MBR_SN),
    UNIQUE KEY UK_COMM_MBR_USR (COMM_SN, USER_SN)
) COMMENT '커뮤니티 멤버';
