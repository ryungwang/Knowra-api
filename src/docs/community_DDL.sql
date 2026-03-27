-- 댓글 반응 (LIKE/LOVE/HAHA/WOW/SAD/ANGRY)
create table tbl_comm_post_cmt_react
(
    CMT_REACT_SN     bigint auto_increment comment '반응 SN (PK)'
        primary key,
    COMM_POST_CMT_SN bigint                               not null comment '댓글 SN (tbl_comm_post_cmt FK)',
    USER_SN          bigint                               not null comment '사용자 SN (TBL_USER FK)',
    REACT_TYP        varchar(10)                          not null comment 'LIKE / LOVE / HAHA / WOW / SAD / ANGRY',
    ACTVTN_YN        char     default 'Y'                 not null comment '활성화 여부',
    CREATR_SN        bigint                               not null comment '생성자 SN',
    FRST_CRT_DT      datetime default current_timestamp() not null comment '최초 생성일시',
    MDFR_SN          bigint                               null comment '수정자 SN',
    MDFCN_DT         datetime                             null on update current_timestamp() comment '수정일시',
    constraint UK_CMT_REACT
        unique (COMM_POST_CMT_SN, USER_SN),
    constraint FK_CMT_REACT_CMT
        foreign key (COMM_POST_CMT_SN) references tbl_comm_post_cmt (COMM_POST_CMT_SN)
)
    comment '커뮤니티 게시글 댓글 반응';


-- auto-generated definition
create table tbl_comm
(
    COMM_SN      bigint auto_increment comment '커뮤니티 SN (PK)'
        primary key,
    COMM_NM      varchar(320)                            not null comment '슬러그 (URL 식별자)',
    COMM_DSPL_NM varchar(100)                            null comment '표시 이름',
    COMM_DESC    varchar(320)                            null comment '설명',
    CTGR_SN      bigint                                  not null comment '카테고리 SN (tbl_ctgr FK)',
    PRVCY_STNG   varchar(20) default 'public'            not null comment 'public / restricted / anonymous / private',
    LOGO_FILE_SN bigint                                  null comment '로고 이미지 (TBL_COM_FILE FK)',
    BNR_FILE_SN  bigint                                  null comment '배너 이미지 (TBL_COM_FILE FK)',
    MEMBER_CNT   bigint      default 0                   not null comment '멤버 수 (캐시)',
    STAT         char        default 'Y'                 not null comment 'Y / N',
    ACTVTN_YN    char        default 'Y'                 not null comment '활성화 여부',
    CREATR_SN    bigint                                  not null comment '생성자 SN',
    FRST_CRT_DT  datetime    default current_timestamp() not null comment '최초 생성일시',
    MDFR_SN      bigint                                  null comment '수정자 SN',
    MDFCN_DT     datetime                                null on update current_timestamp() comment '수정일시',
    constraint COMM_NM
        unique (COMM_NM)
)
    comment '커뮤니티';

-- auto-generated definition
create table tbl_comm_mbr
(
    MBR_SN      bigint auto_increment comment '멤버 SN (PK)'
        primary key,
    COMM_SN     bigint                                  not null comment '커뮤니티 SN (tbl_comm FK)',
    USER_SN     bigint                                  not null comment '사용자 SN (TBL_USER FK)',
    ROLE        varchar(20) default 'MEMBER'            not null comment 'OWNER / ADMIN / MEMBER',
    JOIN_TYP    varchar(20) default 'APPLY'             not null comment 'APPLY / INVITE / AUTO — 백엔드가 PRVCY_STNG 보고 결정',
    STAT        varchar(20) default 'ACTIVE'            not null comment 'PENDING / ACTIVE / REJECTED / BANNED / WITHDRAWN',
    ACTVTN_YN   char        default 'Y'                 not null comment '활성화 여부',
    CREATR_SN   bigint                                  not null comment '생성자 SN',
    FRST_CRT_DT datetime    default current_timestamp() not null comment '최초 생성일시',
    MDFR_SN     bigint                                  null comment '수정자 SN',
    MDFCN_DT    datetime                                null on update current_timestamp() comment '수정일시',
    constraint UK_COMM_MBR_USR
        unique (COMM_SN, USER_SN)
)
    comment '커뮤니티 멤버';

-- auto-generated definition
create table tbl_comm_post
(
    COMM_POST_SN bigint auto_increment comment '커뮤니티 게시글 SN (PK)'
        primary key,
    COMM_SN      bigint                                  not null comment '커뮤니티 SN (tbl_comm FK)',
    POST_TYP     varchar(10) default 'NORMAL'            not null comment 'NORMAL / NOTICE',
    USER_SN      bigint                                  not null comment '작성자 SN (TBL_USER FK)',
    POST_TTL     varchar(300)                            not null comment '제목',
    POST_CNTNT   text                                    not null comment '본문',
    VIEW_CNT     int         default 0                   not null comment '조회수 (캐시)',
    LIKE_CNT     int         default 0                   not null comment '좋아요수 (캐시)',
    CMT_CNT      int         default 0                   not null comment '댓글수 (캐시)',
    STAT         varchar(20) default 'ACTIVE'            not null comment 'ACTIVE / DELETED / BLOCKED',
    ACTVTN_YN    char        default 'Y'                 not null comment '활성화 여부',
    CREATR_SN    bigint                                  not null comment '생성자 SN',
    FRST_CRT_DT  datetime    default current_timestamp() not null comment '최초 생성일시',
    MDFR_SN      bigint                                  null comment '수정자 SN',
    MDFCN_DT     datetime                                null on update current_timestamp() comment '수정일시',
    constraint FK_COMM_POST_COMM
        foreign key (COMM_SN) references tbl_comm (COMM_SN),
    constraint FK_COMM_POST_USER
        foreign key (USER_SN) references knowra_user.tbl_user (USER_SN)
)
    comment '커뮤니티 게시글';

create index IDX_COMM_POST_COMM
    on tbl_comm_post (COMM_SN);

create index IDX_COMM_POST_FEED
    on tbl_comm_post (COMM_SN, STAT, ACTVTN_YN, FRST_CRT_DT);

create index IDX_COMM_POST_FRST_CRT_DT
    on tbl_comm_post (FRST_CRT_DT);

create index IDX_COMM_POST_USER
    on tbl_comm_post (USER_SN);

-- auto-generated definition
create table tbl_comm_post_cmt
(
    COMM_POST_CMT_SN bigint auto_increment comment '댓글 SN (PK)'
        primary key,
    COMM_POST_SN     bigint                                  not null comment '커뮤니티 게시글 SN (tbl_comm_post FK)',
    USER_SN          bigint                                  not null comment '작성자 SN (TBL_USER FK)',
    PRNT_CMT_SN      bigint                                  null comment '부모 댓글 SN — NULL 이면 최상위',
    CMT_CNTNT        text                                    not null comment '댓글 내용',
    LIKE_CNT         int         default 0                   not null comment '좋아요수 (캐시)',
    STAT             varchar(20) default 'ACTIVE'            not null comment 'ACTIVE / DELETED / BLOCKED',
    ACTVTN_YN        char        default 'Y'                 not null comment '활성화 여부',
    CREATR_SN        bigint                                  not null comment '생성자 SN',
    FRST_CRT_DT      datetime    default current_timestamp() not null comment '최초 생성일시',
    MDFR_SN          bigint                                  null comment '수정자 SN',
    MDFCN_DT         datetime                                null on update current_timestamp() comment '수정일시',
    constraint FK_COMM_POST_CMT_POST
        foreign key (COMM_POST_SN) references tbl_comm_post (COMM_POST_SN),
    constraint FK_COMM_POST_CMT_PRNT
        foreign key (PRNT_CMT_SN) references tbl_comm_post_cmt (COMM_POST_CMT_SN),
    constraint FK_COMM_POST_CMT_USER
        foreign key (USER_SN) references knowra_user.tbl_user (USER_SN)
)
    comment '커뮤니티 게시글 댓글';

create index IDX_COMM_POST_CMT_POST
    on tbl_comm_post_cmt (COMM_POST_SN);

create index IDX_COMM_POST_CMT_PRNT
    on tbl_comm_post_cmt (PRNT_CMT_SN);

-- auto-generated definition
create table tbl_comm_post_like
(
    COMM_POST_LIKE_SN bigint auto_increment comment '좋아요 SN (PK)'
        primary key,
    COMM_POST_SN      bigint                                 not null comment '커뮤니티 게시글 SN (tbl_comm_post FK)',
    USER_SN           bigint                                 not null comment '사용자 SN (TBL_USER FK)',
    LIKE_TYP          varchar(4) default 'UP'                not null comment 'UP / DOWN',
    ACTVTN_YN         char       default 'Y'                 not null comment '활성화 여부',
    CREATR_SN         bigint                                 not null comment '생성자 SN',
    FRST_CRT_DT       datetime   default current_timestamp() not null comment '최초 생성일시',
    MDFR_SN           bigint                                 null comment '수정자 SN',
    MDFCN_DT          datetime                               null on update current_timestamp() comment '수정일시',
    constraint UK_COMM_POST_LIKE_USR
        unique (COMM_POST_SN, USER_SN),
    constraint FK_COMM_POST_LIKE_POST
        foreign key (COMM_POST_SN) references tbl_comm_post (COMM_POST_SN),
    constraint FK_COMM_POST_LIKE_USER
        foreign key (USER_SN) references knowra_user.tbl_user (USER_SN)
)
    comment '커뮤니티 게시글 좋아요';

-- auto-generated definition
create table tbl_comm_post_tag
(
    COMM_POST_TAG_SN bigint auto_increment comment '커뮤니티 게시글-태그 SN (PK)'
        primary key,
    COMM_POST_SN     bigint                               not null comment '커뮤니티 게시글 SN (tbl_comm_post FK)',
    TAG_SN           bigint                               not null comment '태그 SN (tbl_tag FK)',
    ACTVTN_YN        char     default 'Y'                 not null comment '활성화 여부',
    CREATR_SN        bigint                               not null comment '생성자 SN',
    FRST_CRT_DT      datetime default current_timestamp() not null comment '최초 생성일시',
    MDFR_SN          bigint                               null comment '수정자 SN',
    MDFCN_DT         datetime                             null on update current_timestamp() comment '수정일시',
    constraint UK_COMM_POST_TAG
        unique (COMM_POST_SN, TAG_SN),
    constraint FK_COMM_POST_TAG_POST
        foreign key (COMM_POST_SN) references tbl_comm_post (COMM_POST_SN),
    constraint FK_COMM_POST_TAG_TAG
        foreign key (TAG_SN) references knowra_com.tbl_tag (TAG_SN)
)
    comment '커뮤니티 게시글-태그 매핑';

