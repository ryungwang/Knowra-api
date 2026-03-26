package com.knowra.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@MappedSuperclass
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
public abstract class BaseCmtEntity extends BaseAuditEntity {

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
}
