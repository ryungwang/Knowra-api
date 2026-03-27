-- auto-generated definition
create table tbl_tag
(
    TAG_SN      bigint auto_increment comment '태그 SN (PK)'
        primary key,
    TAG_NM      varchar(100)                         not null comment '태그명 (예: #Python)',
    USE_COUNT   bigint   default 0                   not null comment '사용횟수',
    ACTVTN_YN   char     default 'Y'                 not null comment '활성화 여부',
    CREATR_SN   bigint                               not null comment '생성자 SN',
    FRST_CRT_DT datetime default current_timestamp() not null comment '최초 생성일시',
    MDFR_SN     bigint                               null comment '수정자 SN',
    MDFCN_DT    datetime                             null on update current_timestamp() comment '수정일시',
    constraint UK_TAG_NM
        unique (TAG_NM)
)
    comment '태그 (일반·커뮤니티 게시글 공용)';

