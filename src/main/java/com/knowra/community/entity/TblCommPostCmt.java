package com.knowra.community.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "TBL_COMM_POST_CMT",
        catalog = "KNOWRA_COMMUNITY",
        indexes = {
                @Index(name = "IDX_COMM_POST_CMT_POST", columnList = "COMM_POST_SN"),
                @Index(name = "IDX_COMM_POST_CMT_PRNT", columnList = "PRNT_CMT_SN")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblCommPostCmt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMM_POST_CMT_SN")
    @Comment("댓글 SN (PK)")
    private Long commPostCmtSn;

    @Column(name = "COMM_POST_SN", nullable = false)
    @Comment("커뮤니티 게시글 SN (tbl_comm_post FK)")
    private long commPostSn;

    @Column(name = "USER_SN", nullable = false)
    @Comment("작성자 SN (TBL_USER FK)")
    private long userSn;

    @Column(name = "PRNT_CMT_SN")
    @Comment("부모 댓글 SN — NULL 이면 최상위")
    private Long prntCmtSn;

    @Column(name = "CMT_CNTNT", columnDefinition = "TEXT", nullable = false)
    @Comment("댓글 내용")
    private String cmtCntnt;

    @Column(name = "LIKE_CNT", nullable = false)
    @Comment("좋아요수 (캐시)")
    @Builder.Default
    private int likeCnt = 0;

    @Column(name = "STAT", length = 20, nullable = false)
    @Comment("ACTIVE / DELETED / BLOCKED")
    @Builder.Default
    private String stat = "ACTIVE";

    @Column(name = "ACTVTN_YN", length = 1, nullable = false)
    @Comment("활성화 여부")
    @Builder.Default
    private String actvtnYn = "Y";

    @Column(name = "CREATR_SN", nullable = false, updatable = false)
    @Comment("생성자 SN")
    private long creatrSn;

    @Column(name = "FRST_CRT_DT", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    @Comment("최초 생성일시")
    @Builder.Default
    private LocalDateTime frstCrtDt = LocalDateTime.now();

    @Column(name = "MDFR_SN", insertable = false)
    @Comment("수정자 SN")
    private Long mdfrSn;

    @Column(name = "MDFCN_DT", columnDefinition = "DATETIME ON UPDATE CURRENT_TIMESTAMP")
    @Comment("수정일시")
    private LocalDateTime mdfcnDt;
}
