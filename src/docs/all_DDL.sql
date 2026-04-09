-- =============================================================
--  Knowra API — 전체 DDL
--  생성 기준: 엔티티 클래스 (2026-04-01)
--
--  카탈로그 구성
--    KNOWRA_USER      사용자
--    KNOWRA_COM       공통 (파일, 태그)
--    KNOWRA_POST      일반 게시글
--    KNOWRA_COMMUNITY 커뮤니티·커뮤니티 게시글
--    KNOWRA_CMS       관리자 (배너, IP)
--    SCHM_CMS         관리자 메뉴
-- =============================================================

-- ============================================================
-- 0. 스키마(카탈로그) 생성
-- ============================================================
CREATE DATABASE IF NOT EXISTS KNOWRA_USER     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS KNOWRA_COM      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS KNOWRA_POST     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS KNOWRA_COMMUNITY CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS KNOWRA_CMS      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS SCHM_CMS        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- ============================================================
-- 1. KNOWRA_USER
-- ============================================================

-- 1-1. TBL_USER
CREATE TABLE IF NOT EXISTS KNOWRA_USER.TBL_USER
(
    USER_SN        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사용자일련번호',
    EMAIL          VARCHAR(320) NOT NULL               COMMENT '사용자이메일',
    LOGIN_ID       VARCHAR(100) NOT NULL               COMMENT '로그인아이디',
    PASSWORD       VARCHAR(200) NOT NULL               COMMENT '비밀번호',
    NAME           VARCHAR(100) NOT NULL               COMMENT '이름',
    BIO            VARCHAR(800)                        COMMENT '자기소개',
    INTEREST       VARCHAR(100) NOT NULL               COMMENT '관심사( CATEGORY - CATEGORY_SN )',
    ATCH_FILE_SN   BIGINT                              COMMENT '프로필 사진 SN',
    JOIN_YMD       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '가입일시',
    LGN_FAIL_NMTM  BIGINT       NOT NULL DEFAULT 0     COMMENT '로그인실패횟수',
    STATUS         VARCHAR(20)  NOT NULL DEFAULT 'Y'   COMMENT '상태',
    ACTVTN_YN      VARCHAR(1)   NOT NULL DEFAULT 'Y'   COMMENT '활성여부',
    CREATR_SN      BIGINT       NOT NULL DEFAULT 1      COMMENT '생성자일련번호',
    FRST_CRT_DT    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초생성일시',
    MDFR_SN        BIGINT                              COMMENT '수정자일련번호',
    MDFCN_DT       DATETIME     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (USER_SN)
) ENGINE = InnoDB COMMENT = '사용자';

-- 1-2. TBL_USER_FLWR (팔로우)
CREATE TABLE IF NOT EXISTS KNOWRA_USER.TBL_USER_FLWR
(
    FLWR_SN        BIGINT NOT NULL AUTO_INCREMENT COMMENT '팔로우 SN (PK)',
    FLWR_USER_SN   BIGINT NOT NULL               COMMENT '팔로우 하는 사용자 SN (follower)',
    FLWNG_USER_SN  BIGINT NOT NULL               COMMENT '팔로우 받는 사용자 SN (followee)',
    ACTVTN_YN      CHAR(1) NOT NULL DEFAULT 'Y'  COMMENT '활성화 여부 (Y: 팔로우 중 / N: 언팔로우)',
    CREATR_SN      BIGINT NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN        BIGINT                        COMMENT '수정자 SN',
    MDFCN_DT       DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (FLWR_SN),
    CONSTRAINT UK_USR_FLWR_REL UNIQUE (FLWR_USER_SN, FLWNG_USER_SN)
) ENGINE = InnoDB COMMENT = '팔로우';

-- 1-3. TBL_USER_LGN_HSTRY (로그인 이력)
CREATE TABLE IF NOT EXISTS KNOWRA_USER.TBL_USER_LGN_HSTRY
(
    LGN_HSTRY_SN  BIGINT   NOT NULL AUTO_INCREMENT COMMENT '로그인이력 SN (PK)',
    USER_SN        BIGINT   NOT NULL               COMMENT '사용자일련번호',
    LGN_DT         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '로그인일시',
    LGN_IP         VARCHAR(45) NOT NULL            COMMENT '로그인 IP',
    ACTVTN_YN      CHAR(1)  NOT NULL DEFAULT 'Y'   COMMENT '활성여부',
    CREATR_SN      BIGINT   NOT NULL DEFAULT 1      COMMENT '생성자일련번호',
    FRST_CRT_DT    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초생성일시',
    PRIMARY KEY (LGN_HSTRY_SN)
) ENGINE = InnoDB COMMENT = '로그인 이력';

-- 1-4. TBL_USER_STNG (개인 설정)
CREATE TABLE IF NOT EXISTS KNOWRA_USER.TBL_USER_STNG
(
    USER_STNG_SN   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '개인 설정 SN (PK)',
    USER_SN        BIGINT      NOT NULL               COMMENT '사용자일련번호',
    THEME_TYP      VARCHAR(20) NOT NULL DEFAULT 'system' COMMENT '테마 유형 (light / dark / system)',
    CMT_NTFCTN_YN  CHAR(1)     NOT NULL DEFAULT 'Y'   COMMENT '댓글 알림 여부 (Y / N)',
    FLWR_NTFCTN_YN CHAR(1)     NOT NULL DEFAULT 'Y'   COMMENT '팔로우 알림 여부 (Y / N)',
    LIKE_NTFCTN_YN CHAR(1)     NOT NULL DEFAULT 'N'   COMMENT '좋아요 알림 여부 (Y / N)',
    SYS_NTFCTN_YN  CHAR(1)     NOT NULL DEFAULT 'Y'   COMMENT '시스템 알림 여부 (Y / N)',
    ACTVTN_YN      VARCHAR(1)  NOT NULL DEFAULT 'Y'   COMMENT '활성여부',
    CREATR_SN      BIGINT      NOT NULL DEFAULT 1      COMMENT '생성자일련번호',
    FRST_CRT_DT    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초생성일시',
    MDFR_SN        BIGINT                             COMMENT '수정자일련번호',
    MDFCN_DT       DATETIME    ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (USER_STNG_SN)
) ENGINE = InnoDB COMMENT = '사용자 개인 설정';

-- 1-5. TBL_USER_TAG (사용자 태그)
CREATE TABLE IF NOT EXISTS KNOWRA_USER.TBL_USER_TAG
(
    USER_TAG_SN  BIGINT  NOT NULL AUTO_INCREMENT COMMENT '사용자 태그 SN (PK)',
    USER_SN      BIGINT  NOT NULL               COMMENT '사용자일련번호',
    TAG_SN       BIGINT  NOT NULL               COMMENT '태그 SN (TBL_TAG FK)',
    USE_COUNT    BIGINT  NOT NULL               COMMENT '사용횟수',
    ACTVTN_YN    CHAR(1) NOT NULL DEFAULT 'Y'   COMMENT '활성여부',
    CREATR_SN    BIGINT  NOT NULL DEFAULT 1      COMMENT '생성자일련번호',
    FRST_CRT_DT  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초생성일시',
    PRIMARY KEY (USER_TAG_SN)
) ENGINE = InnoDB COMMENT = '사용자 태그';


-- 1-6. TBL_USER_ACTION_LOG (행동 로그)
CREATE TABLE IF NOT EXISTS KNOWRA_USER.TBL_USER_ACTION_LOG
(
    ACTION_SN   BIGINT      NOT NULL AUTO_INCREMENT            COMMENT '행동 로그 SN (PK)',
    USER_SN     BIGINT      NOT NULL                           COMMENT '행동 주체 유저 SN',
    TARGET_TYPE VARCHAR(20) NOT NULL                           COMMENT 'COMM | COMM_POST | POST | USER',
    TARGET_SN   BIGINT      NOT NULL                           COMMENT '대상 SN',
    ACTION_TYPE VARCHAR(20) NOT NULL                           COMMENT 'VIEW | LIKE | COMMENT | SCRAP | POST | JOIN | FOLLOW',
    REG_DT      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '행동 발생 일시',
    PRIMARY KEY (ACTION_SN),
    INDEX IDX_ACTION_USER_TARGET (USER_SN, TARGET_TYPE, TARGET_SN),
    INDEX IDX_ACTION_REG_DT      (REG_DT)
) ENGINE = InnoDB COMMENT = '유저 행동 로그';

-- 1-7. TBL_USER_INTEREST_SCORE (관심도 점수)
CREATE TABLE IF NOT EXISTS KNOWRA_USER.TBL_USER_INTEREST_SCORE
(
    SCORE_SN    BIGINT         NOT NULL AUTO_INCREMENT                              COMMENT '관심도 점수 SN (PK)',
    USER_SN     BIGINT         NOT NULL                                             COMMENT '유저 SN',
    TARGET_TYPE VARCHAR(20)    NOT NULL                                             COMMENT 'COMM | COMM_POST | POST | USER',
    TARGET_SN   BIGINT         NOT NULL                                             COMMENT '대상 SN',
    SCORE       DECIMAL(10, 4) NOT NULL DEFAULT 0                                  COMMENT '누적 관심도 점수 (0 이상)',
    UPDT_DT     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최종 업데이트 일시',
    PRIMARY KEY (SCORE_SN),
    UNIQUE KEY UK_INTEREST_USER_TARGET (USER_SN, TARGET_TYPE, TARGET_SN)
) ENGINE = InnoDB COMMENT = '유저 관심도 점수 (현재 상태 스냅샷)';


-- 1-8. TBL_USER_NOTIF (알림)
CREATE TABLE IF NOT EXISTS KNOWRA_USER.TBL_USER_NOTIF
(
    NOTIF_SN    BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '알림일련번호',
    USER_SN     BIGINT       NOT NULL                                   COMMENT '수신자 SN',
    SENDER_SN   BIGINT                                                  COMMENT '발신자 SN (SYSTEM 공지는 NULL)',
    NOTIF_TYP   VARCHAR(20)  NOT NULL                                   COMMENT '알림유형 COMMENT|LIKE|FOLLOW|SYSTEM',
    MESSAGE     VARCHAR(255) NOT NULL                                   COMMENT '알림메시지',
    TARGET_SN   BIGINT                                                  COMMENT '대상 게시글/유저 SN',
    TARGET_KIND VARCHAR(20)                                             COMMENT '대상종류 POST|COMM_POST',
    IS_READ     CHAR(1)      NOT NULL DEFAULT 'N'                       COMMENT '읽음여부',
    FRST_CRT_DT DATETIME     NOT NULL DEFAULT NOW()                     COMMENT '생성일시',
    PRIMARY KEY (NOTIF_SN),
    CONSTRAINT FK_NOTIF_USER   FOREIGN KEY (USER_SN)   REFERENCES KNOWRA_USER.TBL_USER (USER_SN),
    CONSTRAINT FK_NOTIF_SENDER FOREIGN KEY (SENDER_SN) REFERENCES KNOWRA_USER.TBL_USER (USER_SN),
    INDEX IDX_NOTIF_USER_SN (USER_SN, FRST_CRT_DT DESC)
) ENGINE = InnoDB COMMENT = '유저 알림';


-- ============================================================
-- 2. KNOWRA_COM
-- ============================================================

-- 2-1. TBL_COM_FILE (파일)
CREATE TABLE IF NOT EXISTS KNOWRA_COM.TBL_COM_FILE
(
    ATCH_FILE_SN      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '파일일련번호',
    STRG_FILE_NM      VARCHAR(100) NOT NULL               COMMENT '저장 파일명 (UUID)',
    ATCH_FILE_NM      VARCHAR(100) NOT NULL               COMMENT '첨부파일명',
    ATCH_FILE_PATH_NM VARCHAR(100) NOT NULL               COMMENT '첨부파일경로',
    ATCH_FILE_SZ      BIGINT       NOT NULL               COMMENT '첨부파일사이즈',
    ATCH_FILE_EXTN_NM VARCHAR(20)  NOT NULL               COMMENT '첨부파일확장자',
    PSN_TBL_SN        VARCHAR(40)  NOT NULL               COMMENT '소유테이블데이터기본키',
    ACTVTN_YN         CHAR(1)      NOT NULL DEFAULT 'Y'   COMMENT '활성여부',
    CREATR_SN         BIGINT       NOT NULL               COMMENT '생성자일련번호',
    FRST_CRT_DT       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초생성일시',
    MDFR_SN           BIGINT                              COMMENT '수정자일련번호',
    MDFCN_DT          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (ATCH_FILE_SN)
) ENGINE = InnoDB COMMENT = '파일';

-- 2-2. TBL_TAG (태그 원본)
CREATE TABLE IF NOT EXISTS KNOWRA_COM.TBL_TAG
(
    TAG_SN       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '태그 SN (PK)',
    TAG_NM       VARCHAR(100) NOT NULL               COMMENT '태그명 (예: #Python)',
    USE_COUNT    BIGINT       NOT NULL DEFAULT 0     COMMENT '사용횟수',
    ACTVTN_YN    CHAR(1)      NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN    BIGINT       NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN      BIGINT                              COMMENT '수정자 SN',
    MDFCN_DT     DATETIME     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (TAG_SN),
    CONSTRAINT UK_TAG_NM UNIQUE (TAG_NM)
) ENGINE = InnoDB COMMENT = '태그';


-- ============================================================
-- 3. KNOWRA_POST
-- ============================================================

-- 3-1. TBL_POST (일반 게시글)
CREATE TABLE IF NOT EXISTS KNOWRA_POST.TBL_POST
(
    POST_SN      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '게시글 SN (PK)',
    POST_TYP     VARCHAR(10)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL / NOTICE',
    USER_SN      BIGINT       NOT NULL               COMMENT '작성자 SN (TBL_USER FK)',
    POST_TTL     VARCHAR(300) NOT NULL               COMMENT '제목',
    POST_CNTNT   TEXT         NOT NULL               COMMENT '본문',
    VIEW_CNT     INT          NOT NULL DEFAULT 0     COMMENT '조회수 (캐시)',
    LIKE_CNT     INT          NOT NULL DEFAULT 0     COMMENT '순 추천수 (UP - DOWN 캐시)',
    CMT_CNT      INT          NOT NULL DEFAULT 0     COMMENT '댓글수 (캐시)',
    STAT         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DELETED / BLOCKED',
    ACTVTN_YN    CHAR(1)      NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN    BIGINT       NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN      BIGINT                              COMMENT '수정자 SN',
    MDFCN_DT     DATETIME     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (POST_SN),
    INDEX IDX_POST_USER (USER_SN),
    INDEX IDX_POST_FRST_CRT_DT (FRST_CRT_DT)
) ENGINE = InnoDB COMMENT = '일반 게시글';

-- 3-2. TBL_POST_CMT (일반 게시글 댓글)
CREATE TABLE IF NOT EXISTS KNOWRA_POST.TBL_POST_CMT
(
    POST_CMT_SN  BIGINT  NOT NULL AUTO_INCREMENT COMMENT '댓글 SN (PK)',
    POST_SN      BIGINT  NOT NULL               COMMENT '게시글 SN (TBL_POST FK)',
    USER_SN      BIGINT  NOT NULL               COMMENT '작성자 SN (TBL_USER FK)',
    PRNT_CMT_SN  BIGINT                         COMMENT '부모 댓글 SN — NULL이면 최상위',
    CMT_CNTNT    TEXT    NOT NULL               COMMENT '댓글 내용',
    LIKE_CNT     INT     NOT NULL DEFAULT 0     COMMENT '좋아요수 (캐시)',
    STAT         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DELETED / BLOCKED',
    ACTVTN_YN    CHAR(1) NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN    BIGINT  NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN      BIGINT                         COMMENT '수정자 SN',
    MDFCN_DT     DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (POST_CMT_SN),
    INDEX IDX_POST_CMT_POST (POST_SN),
    INDEX IDX_POST_CMT_PRNT (PRNT_CMT_SN)
) ENGINE = InnoDB COMMENT = '일반 게시글 댓글';

-- 3-3. TBL_POST_LIKE (일반 게시글 좋아요)
CREATE TABLE IF NOT EXISTS KNOWRA_POST.TBL_POST_LIKE
(
    POST_LIKE_SN  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '좋아요 SN (PK)',
    POST_SN       BIGINT      NOT NULL               COMMENT '게시글 SN (TBL_POST FK)',
    USER_SN       BIGINT      NOT NULL               COMMENT '사용자 SN (TBL_USER FK)',
    LIKE_TYP      VARCHAR(4)  NOT NULL DEFAULT 'UP'  COMMENT 'UP / DOWN',
    ACTVTN_YN     CHAR(1)     NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN     BIGINT      NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN       BIGINT                             COMMENT '수정자 SN',
    MDFCN_DT      DATETIME    ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (POST_LIKE_SN),
    CONSTRAINT UK_POST_LIKE_USR UNIQUE (POST_SN, USER_SN)
) ENGINE = InnoDB COMMENT = '일반 게시글 좋아요';

-- 3-4. TBL_POST_SAVE (게시글 저장)
--   주의: 엔티티의 @UniqueConstraint·@Index에서 POST_TYP 참조 → 실제 컬럼명은 POST_KIND
CREATE TABLE IF NOT EXISTS KNOWRA_POST.TBL_POST_SAVE
(
    POST_SAVE_SN  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '저장 SN (PK)',
    USER_SN       BIGINT      NOT NULL               COMMENT '사용자 SN (TBL_USER FK)',
    POST_SN       BIGINT      NOT NULL               COMMENT '게시글 SN (논리 참조)',
    POST_KIND     VARCHAR(10) NOT NULL               COMMENT '게시글 유형: POST / COMM',
    ACTVTN_YN     CHAR(1)     NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN     BIGINT      NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN       BIGINT                             COMMENT '수정자 SN',
    MDFCN_DT      DATETIME    ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (POST_SAVE_SN),
    CONSTRAINT UK_POST_SAVE_USR UNIQUE (USER_SN, POST_KIND, POST_SN),
    INDEX IDX_POST_SAVE_POST    (POST_KIND, POST_SN),
    INDEX IDX_POST_SAVE_USER_DT (USER_SN, FRST_CRT_DT)
) ENGINE = InnoDB COMMENT = '게시글 저장';

-- 3-5. TBL_POST_TAG (일반 게시글 태그 매핑)
CREATE TABLE IF NOT EXISTS KNOWRA_POST.TBL_POST_TAG
(
    POST_TAG_SN  BIGINT  NOT NULL AUTO_INCREMENT COMMENT '게시글-태그 SN (PK)',
    POST_SN      BIGINT  NOT NULL               COMMENT '게시글 SN (TBL_POST FK)',
    TAG_SN       BIGINT  NOT NULL               COMMENT '태그 SN (TBL_TAG FK)',
    ACTVTN_YN    CHAR(1) NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN    BIGINT  NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN      BIGINT                         COMMENT '수정자 SN',
    MDFCN_DT     DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (POST_TAG_SN),
    CONSTRAINT UK_POST_TAG UNIQUE (POST_SN, TAG_SN)
) ENGINE = InnoDB COMMENT = '일반 게시글 태그 매핑';


-- ============================================================
-- 4. KNOWRA_COMMUNITY
-- ============================================================

-- 4-1. TBL_COMM (커뮤니티)
CREATE TABLE IF NOT EXISTS KNOWRA_COMMUNITY.TBL_COMM
(
    COMM_SN       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '커뮤니티 SN (PK)',
    COMM_NM       VARCHAR(320) NOT NULL               COMMENT '슬러그 (URL 식별자)',
    COMM_DSPL_NM  VARCHAR(100)                        COMMENT '표시 이름',
    COMM_DESC     VARCHAR(320)                        COMMENT '설명',
    CTGR_SN       BIGINT       NOT NULL               COMMENT '카테고리 SN',
    PRVCY_STNG    VARCHAR(20)  NOT NULL               COMMENT 'public / restricted / anonymous / private',
    LOGO_FILE_SN  BIGINT                              COMMENT '로고 이미지 (TBL_COM_FILE FK)',
    BNR_FILE_SN   BIGINT                              COMMENT '배너 이미지 (TBL_COM_FILE FK)',
    MEMBER_CNT    BIGINT       NOT NULL DEFAULT 0     COMMENT '멤버 수 (캐시)',
    STAT          CHAR(1)      NOT NULL DEFAULT 'Y'   COMMENT 'Y / N',
    ACTVTN_YN     CHAR(1)      NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN     BIGINT       NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN       BIGINT                              COMMENT '수정자 SN',
    MDFCN_DT      DATETIME     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (COMM_SN),
    CONSTRAINT UK_COMM_NM UNIQUE (COMM_NM)
) ENGINE = InnoDB COMMENT = '커뮤니티';

-- 4-2. TBL_COMM_MBR (커뮤니티 멤버)
CREATE TABLE IF NOT EXISTS KNOWRA_COMMUNITY.TBL_COMM_MBR
(
    MBR_SN       BIGINT      NOT NULL AUTO_INCREMENT COMMENT '멤버 SN (PK)',
    COMM_SN      BIGINT      NOT NULL               COMMENT '커뮤니티 SN (TBL_COMM FK)',
    USER_SN      BIGINT      NOT NULL               COMMENT '사용자 SN (TBL_USER FK)',
    ROLE         VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER / ADMIN / MEMBER',
    JOIN_TYP     VARCHAR(20) NOT NULL DEFAULT 'APPLY'  COMMENT 'APPLY / INVITE / AUTO',
    STAT         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'  COMMENT 'PENDING / ACTIVE / REJECTED / BANNED / WITHDRAWN',
    ACTVTN_YN    CHAR(1)     NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN    BIGINT      NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN      BIGINT                             COMMENT '수정자 SN',
    MDFCN_DT     DATETIME    ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (MBR_SN),
    CONSTRAINT UK_COMM_MBR_USR UNIQUE (COMM_SN, USER_SN)
) ENGINE = InnoDB COMMENT = '커뮤니티 멤버';

-- 4-3. TBL_COMM_POST (커뮤니티 게시글)
CREATE TABLE IF NOT EXISTS KNOWRA_COMMUNITY.TBL_COMM_POST
(
    COMM_POST_SN  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '커뮤니티 게시글 SN (PK)',
    COMM_SN       BIGINT       NOT NULL               COMMENT '커뮤니티 SN (TBL_COMM FK)',
    POST_TYP      VARCHAR(10)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL / NOTICE',
    USER_SN       BIGINT       NOT NULL               COMMENT '작성자 SN (TBL_USER FK)',
    POST_TTL      VARCHAR(300) NOT NULL               COMMENT '제목',
    POST_CNTNT    TEXT         NOT NULL               COMMENT '본문',
    VIEW_CNT      INT          NOT NULL DEFAULT 0     COMMENT '조회수 (캐시, Redis 동기화)',
    LIKE_CNT      INT          NOT NULL DEFAULT 0     COMMENT '순 추천수 (UP - DOWN 캐시)',
    CMT_CNT       INT          NOT NULL DEFAULT 0     COMMENT '댓글수 (캐시)',
    STAT          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DELETED / BLOCKED',
    ACTVTN_YN     CHAR(1)      NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN     BIGINT       NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN       BIGINT                              COMMENT '수정자 SN',
    MDFCN_DT      DATETIME     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (COMM_POST_SN),
    INDEX IDX_COMM_POST_COMM        (COMM_SN),
    INDEX IDX_COMM_POST_USER        (USER_SN),
    INDEX IDX_COMM_POST_FRST_CRT_DT (FRST_CRT_DT)
) ENGINE = InnoDB COMMENT = '커뮤니티 게시글';

-- 4-4. TBL_COMM_POST_CMT (커뮤니티 게시글 댓글)
CREATE TABLE IF NOT EXISTS KNOWRA_COMMUNITY.TBL_COMM_POST_CMT
(
    COMM_POST_CMT_SN  BIGINT  NOT NULL AUTO_INCREMENT COMMENT '댓글 SN (PK)',
    COMM_POST_SN      BIGINT  NOT NULL               COMMENT '커뮤니티 게시글 SN (TBL_COMM_POST FK)',
    USER_SN           BIGINT  NOT NULL               COMMENT '작성자 SN (TBL_USER FK)',
    PRNT_CMT_SN       BIGINT                         COMMENT '부모 댓글 SN — NULL이면 최상위',
    CMT_CNTNT         TEXT    NOT NULL               COMMENT '댓글 내용',
    LIKE_CNT          INT     NOT NULL DEFAULT 0     COMMENT '좋아요수 (캐시)',
    STAT              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DELETED / BLOCKED',
    ACTVTN_YN         CHAR(1) NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN         BIGINT  NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN           BIGINT                         COMMENT '수정자 SN',
    MDFCN_DT          DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (COMM_POST_CMT_SN),
    INDEX IDX_COMM_POST_CMT_POST (COMM_POST_SN),
    INDEX IDX_COMM_POST_CMT_PRNT (PRNT_CMT_SN)
) ENGINE = InnoDB COMMENT = '커뮤니티 게시글 댓글';

-- 4-5. TBL_COMM_POST_CMT_REACT (커뮤니티 댓글 반응)
CREATE TABLE IF NOT EXISTS KNOWRA_COMMUNITY.TBL_COMM_POST_CMT_REACT
(
    CMT_REACT_SN      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '반응 SN (PK)',
    COMM_POST_CMT_SN  BIGINT      NOT NULL               COMMENT '댓글 SN (TBL_COMM_POST_CMT FK)',
    USER_SN           BIGINT      NOT NULL               COMMENT '사용자 SN (TBL_USER FK)',
    REACT_TYP         VARCHAR(10) NOT NULL               COMMENT 'LIKE / LOVE / HAHA / WOW / SAD / ANGRY',
    ACTVTN_YN         CHAR(1)     NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN         BIGINT      NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN           BIGINT                             COMMENT '수정자 SN',
    MDFCN_DT          DATETIME    ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (CMT_REACT_SN),
    CONSTRAINT UK_COMM_CMT_REACT UNIQUE (COMM_POST_CMT_SN, USER_SN)
) ENGINE = InnoDB COMMENT = '커뮤니티 댓글 반응';

-- 4-6. TBL_POST_CMT_REACT (일반 게시글 댓글 반응)
--   주의: 엔티티 catalog = "KNOWRA_COMMUNITY" 로 선언됨
CREATE TABLE IF NOT EXISTS KNOWRA_COMMUNITY.TBL_POST_CMT_REACT
(
    CMT_REACT_SN  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '반응 SN (PK)',
    POST_CMT_SN   BIGINT      NOT NULL               COMMENT '댓글 SN (TBL_POST_CMT FK)',
    USER_SN       BIGINT      NOT NULL               COMMENT '사용자 SN (TBL_USER FK)',
    REACT_TYP     VARCHAR(10) NOT NULL               COMMENT 'LIKE / LOVE / HAHA / WOW / SAD / ANGRY',
    ACTVTN_YN     CHAR(1)     NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN     BIGINT      NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN       BIGINT                             COMMENT '수정자 SN',
    MDFCN_DT      DATETIME    ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (CMT_REACT_SN),
    CONSTRAINT UK_POST_CMT_REACT UNIQUE (POST_CMT_SN, USER_SN)
) ENGINE = InnoDB COMMENT = '일반 게시글 댓글 반응';

-- 4-7. TBL_COMM_POST_LIKE (커뮤니티 게시글 좋아요)
CREATE TABLE IF NOT EXISTS KNOWRA_COMMUNITY.TBL_COMM_POST_LIKE
(
    COMM_POST_LIKE_SN  BIGINT     NOT NULL AUTO_INCREMENT COMMENT '좋아요 SN (PK)',
    COMM_POST_SN       BIGINT     NOT NULL               COMMENT '커뮤니티 게시글 SN (TBL_COMM_POST FK)',
    USER_SN            BIGINT     NOT NULL               COMMENT '사용자 SN (TBL_USER FK)',
    LIKE_TYP           VARCHAR(4) NOT NULL DEFAULT 'UP'  COMMENT 'UP / DOWN',
    ACTVTN_YN          CHAR(1)    NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN          BIGINT     NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT        DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN            BIGINT                            COMMENT '수정자 SN',
    MDFCN_DT           DATETIME   ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (COMM_POST_LIKE_SN),
    CONSTRAINT UK_COMM_POST_LIKE_USR UNIQUE (COMM_POST_SN, USER_SN)
) ENGINE = InnoDB COMMENT = '커뮤니티 게시글 좋아요';

-- 4-8. TBL_COMM_POST_TAG (커뮤니티 게시글 태그 매핑)
CREATE TABLE IF NOT EXISTS KNOWRA_COMMUNITY.TBL_COMM_POST_TAG
(
    COMM_POST_TAG_SN  BIGINT  NOT NULL AUTO_INCREMENT COMMENT '커뮤니티 게시글-태그 SN (PK)',
    COMM_POST_SN      BIGINT  NOT NULL               COMMENT '커뮤니티 게시글 SN (TBL_COMM_POST FK)',
    TAG_SN            BIGINT  NOT NULL               COMMENT '태그 SN (TBL_TAG FK)',
    ACTVTN_YN         CHAR(1) NOT NULL DEFAULT 'Y'   COMMENT '활성화 여부',
    CREATR_SN         BIGINT  NOT NULL               COMMENT '생성자 SN',
    FRST_CRT_DT       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초 생성일시',
    MDFR_SN           BIGINT                         COMMENT '수정자 SN',
    MDFCN_DT          DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (COMM_POST_TAG_SN),
    CONSTRAINT UK_COMM_POST_TAG UNIQUE (COMM_POST_SN, TAG_SN)
) ENGINE = InnoDB COMMENT = '커뮤니티 게시글 태그 매핑';


-- ============================================================
-- 5. KNOWRA_CMS
-- ============================================================

-- 5-1. TBL_BNR (배너)
CREATE TABLE IF NOT EXISTS KNOWRA_CMS.TBL_BNR
(
    BNR_SN       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '배너일련번호',
    BNR_KND      VARCHAR(50)  NOT NULL               COMMENT '배너종류',
    BNR_TTL      VARCHAR(200) NOT NULL               COMMENT '배너제목',
    BNR_URL_ADDR VARCHAR(500) NOT NULL               COMMENT '배너URL주소',
    START_DT     VARCHAR(20)  NOT NULL               COMMENT '시작일 (YYYYMMDD)',
    END_DT       VARCHAR(20)  NOT NULL               COMMENT '종료일 (YYYYMMDD)',
    ACTVTN_YN    VARCHAR(1)   NOT NULL DEFAULT 'Y'   COMMENT '활성여부',
    CREATR_SN    BIGINT       NOT NULL               COMMENT '생성자일련번호',
    FRST_CRT_DT  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초생성일시',
    MDFR_SN      BIGINT                              COMMENT '수정자일련번호',
    MDFCN_DT     DATETIME     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (BNR_SN)
) ENGINE = InnoDB COMMENT = '배너';

-- 5-2. TBL_ACS_IP (관리자 접근 허용 IP)
CREATE TABLE IF NOT EXISTS KNOWRA_CMS.TBL_ACS_IP
(
    ACS_IP_SN    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '관리자접근일련번호',
    IP_ADDR      VARCHAR(45)  NOT NULL               COMMENT 'IP주소',
    PLCUS_NM     VARCHAR(200) NOT NULL               COMMENT '사용처명',
    ACTVTN_YN    VARCHAR(1)   NOT NULL DEFAULT 'Y'   COMMENT '활성여부',
    CREATR_SN    BIGINT       NOT NULL               COMMENT '생성자일련번호',
    FRST_CRT_DT  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초생성일시',
    MDFR_SN      BIGINT                              COMMENT '수정자일련번호',
    MDFCN_DT     DATETIME     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (ACS_IP_SN)
) ENGINE = InnoDB COMMENT = '관리자 접근 허용 IP';


-- ============================================================
-- 6. SCHM_CMS
-- ============================================================

-- 6-1. TBL_MENU (메뉴)
CREATE TABLE IF NOT EXISTS SCHM_CMS.TBL_MENU
(
    MENU_SN         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '메뉴일련번호',
    MENU_TYPE       VARCHAR(50)  NOT NULL               COMMENT '메뉴유형',
    BBS_SN          BIGINT                              COMMENT '게시판일련번호',
    MENU_NAME       VARCHAR(200) NOT NULL               COMMENT '메뉴명',
    MENU_SORT_ORDER BIGINT       NOT NULL               COMMENT '메뉴정렬',
    MENU_PATH       VARCHAR(300) NOT NULL               COMMENT '메뉴경로',
    APLCN_NTN_LTR   VARCHAR(20)  NOT NULL DEFAULT 'EN'  COMMENT '적용국가문자',
    MENU_NM_PATH    VARCHAR(300)                        COMMENT '메뉴이름경로',
    MENU_SN_PATH    VARCHAR(300)                        COMMENT '메뉴일련번호경로',
    MENU_WHOL_PATH  VARCHAR(300)                        COMMENT '메뉴전체경로',
    ACTVTN_YN       VARCHAR(1)   NOT NULL DEFAULT 'Y'   COMMENT '활성여부',
    CREATR_SN       BIGINT       NOT NULL               COMMENT '생성자일련번호',
    FRST_CRT_DT     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최초생성일시',
    MDFR_SN         BIGINT                              COMMENT '수정자일련번호',
    MDFCN_DT        DATETIME     ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    PRIMARY KEY (MENU_SN)
) ENGINE = InnoDB COMMENT = '메뉴';