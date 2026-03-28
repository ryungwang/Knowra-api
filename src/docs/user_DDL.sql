-- auto-generated definition
create table tbl_user
(
    USER_SN       bigint auto_increment comment '사용자일련번호'
        primary key,
    EMAIL         varchar(100)                            not null comment '이메일',
    LOGIN_ID      varchar(100)                            not null comment '로그인 아이디',
    PASSWORD      varchar(200)                            not null comment '비밀번호',
    NAME          varchar(100)                            not null comment '이름',
    JOIN_YMD      datetime    default current_timestamp() not null comment '가입일시',
    LGN_FAIL_NMTM int(10)     default 0                   not null comment '로그인실패횟수',
    STATUS        varchar(20) default 'Y'                 not null comment '회원상태',
    ACTVTN_YN     varchar(1)  default 'Y'                 not null comment '활성여부',
    CREATR_SN     int(10)                                 not null comment '생성자일련번호',
    FRST_CRT_DT   datetime    default current_timestamp() not null comment '최초생성일시',
    MDFR_SN       int(10)                                 null comment '수정자일련번호',
    MDFCN_DT      datetime                                null on update current_timestamp() comment '수정일'
)
    comment '사용자';


-- ============================================================
--  사용자 팔로우
--  FLWR_USER_SN  : 팔로우를 건 사람 (follower)
--  FLWNG_USER_SN : 팔로우를 받는 사람 (followee / following target)
-- ============================================================
CREATE TABLE knowra_user.tbl_usr_flwr (
                                          FLWR_SN         BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '팔로우 SN (PK)',
                                          FLWR_USER_SN    BIGINT      NOT NULL                 COMMENT '팔로우 하는 사용자 SN (TBL_USER FK — follower)',
                                          FLWNG_USER_SN   BIGINT      NOT NULL                 COMMENT '팔로우 받는 사용자 SN (TBL_USER FK — followee)',
                                          ACTVTN_YN       CHAR(1)     NOT NULL DEFAULT 'Y'     COMMENT '활성화 여부 (Y: 팔로우 중 / N: 언팔로우)',
                                          CREATR_SN       BIGINT      NOT NULL                 COMMENT '생성자 SN',
                                          FRST_CRT_DT     DATETIME    NOT NULL DEFAULT NOW()   COMMENT '최초 생성일시',
                                          MDFR_SN         BIGINT      NULL                     COMMENT '수정자 SN',
                                          MDFCN_DT        DATETIME    NULL ON UPDATE NOW()     COMMENT '수정일시',

                                          PRIMARY KEY (FLWR_SN),

    -- 동일 팔로우 관계 중복 방지
                                          UNIQUE KEY  UK_USR_FLWR_REL     (FLWR_USER_SN, FLWNG_USER_SN),

    -- 자기 자신 팔로우 방지는 애플리케이션 레이어에서 처리

    -- 팔로워 목록 조회: FLWNG_USER_SN 기준 검색
                                          INDEX       IDX_USR_FLWR_FLWNG  (FLWNG_USER_SN),

                                          CONSTRAINT  FK_USR_FLWR_FLWR    FOREIGN KEY (FLWR_USER_SN)  REFERENCES knowra_user.TBL_USER (USER_SN),
                                          CONSTRAINT  FK_USR_FLWR_FLWNG   FOREIGN KEY (FLWNG_USER_SN) REFERENCES knowra_user.TBL_USER (USER_SN)
) COMMENT '사용자 팔로우';

CREATE TABLE knowra_user.tbl_usr_tag (
                                         USR_TAG_SN  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '사용자 태그 SN (PK)',
                                         USER_SN     BIGINT          NOT NULL                 COMMENT '사용자 SN (TBL_USER FK)',
                                         TAG_NM      VARCHAR(100)    NOT NULL                 COMMENT '태그명',
                                         FRST_CRT_DT DATETIME        NOT NULL DEFAULT NOW()   COMMENT '최초 생성일시',
                                         PRIMARY KEY (USR_TAG_SN),
                                         UNIQUE KEY UK_USR_TAG (USER_SN, TAG_NM)
) COMMENT '사용자 관심 태그';
