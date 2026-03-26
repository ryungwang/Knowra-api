package com.knowra.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "TBL_POST_LIKE",
        catalog = "KNOWRA_COM",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_POST_LIKE_USR",
                columnNames = {"POST_SN", "USER_SN"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblPostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "POST_LIKE_SN")
    @Comment("좋아요 SN (PK)")
    private Long postLikeSn;

    @Column(name = "POST_SN", nullable = false)
    @Comment("게시글 SN (tbl_post FK)")
    private long postSn;

    @Column(name = "USER_SN", nullable = false)
    @Comment("사용자 SN (TBL_USER FK)")
    private long userSn;

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
