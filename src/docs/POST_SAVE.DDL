CREATE TABLE knowra_post.tbl_post_save (
                                           POST_SAVE_SN BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '저장 SN (PK)',
                                           USER_SN      BIGINT      NOT NULL                 COMMENT '사용자 SN (TBL_USER FK)',
                                           POST_SN      BIGINT      NOT NULL                 COMMENT '게시글 SN (POST_TYP에 따라 tbl_post 또는 tbl_comm_post 논리 참조)',
                                           POST_TYP     VARCHAR(10) NOT NULL                 COMMENT '게시글 유형: POST / COMM',
                                           ACTVTN_YN    CHAR(1)     NOT NULL DEFAULT 'Y'    COMMENT '활성화 여부 (Y: 저장 / N: 저장 취소)',
                                           CREATR_SN    BIGINT      NOT NULL                 COMMENT '생성자 SN',
                                           FRST_CRT_DT  DATETIME    NOT NULL DEFAULT NOW()   COMMENT '최초 생성일시',
                                           MDFR_SN      BIGINT      NULL                     COMMENT '수정자 SN',
                                           MDFCN_DT     DATETIME    NULL ON UPDATE NOW()     COMMENT '수정일시',

                                           PRIMARY KEY (POST_SAVE_SN),
                                           UNIQUE KEY UK_POST_SAVE_USR (USER_SN, POST_TYP, POST_SN),
                                           CONSTRAINT FK_POST_SAVE_USER FOREIGN KEY (USER_SN) REFERENCES knowra_user.TBL_USER (USER_SN),
                                           INDEX IDX_POST_SAVE_POST (POST_TYP, POST_SN),
                                           INDEX IDX_POST_SAVE_USER_DT (USER_SN, FRST_CRT_DT)
) COMMENT '게시글 저장 (일반·커뮤니티 게시글 통합)';