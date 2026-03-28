package com.knowra.post.entity;

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
public abstract class BasePostEntity extends BaseAuditEntity {

    @Column(name = "USER_SN", nullable = false)
    @Comment("작성자 SN (TBL_USER FK)")
    private long userSn;

    @Column(name = "POST_TTL", length = 300, nullable = false)
    @Comment("제목")
    private String postTtl;

    @Column(name = "POST_CNTNT", columnDefinition = "TEXT", nullable = false)
    @Comment("본문")
    private String postCntnt;

    @Column(name = "VIEW_CNT", nullable = false)
    @Comment("조회수 (캐시)")
    @Builder.Default
    private int viewCnt = 0;

    @Column(name = "LIKE_CNT", nullable = false)
    @Comment("순 추천수 (UP - DOWN 캐시)")
    @Builder.Default
    private int likeCnt = 0;

    @Column(name = "CMT_CNT", nullable = false)
    @Comment("댓글수 (캐시)")
    @Builder.Default
    private int cmtCnt = 0;

    @Column(name = "STAT", length = 20, nullable = false)
    @Comment("ACTIVE / DELETED / BLOCKED")
    @Builder.Default
    private String stat = "ACTIVE";

    @Column(name = "ACTVTN_YN", length = 1, nullable = false)
    @Comment("활성화 여부")
    @Builder.Default
    private String actvtnYn = "Y";
}
