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

