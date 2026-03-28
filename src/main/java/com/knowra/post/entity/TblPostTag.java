package com.knowra.post.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "TBL_POST_TAG",
        catalog = "KNOWRA_POST",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_POST_TAG",
                columnNames = {"POST_SN", "TAG_SN"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblPostTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "POST_TAG_SN")
    @Comment("게시글-태그 SN (PK)")
    private Long postTagSn;

    @Column(name = "POST_SN", nullable = false)
    @Comment("게시글 SN (tbl_post FK)")
    private long postSn;

    @Column(name = "TAG_SN", nullable = false)
    @Comment("태그 SN (tbl_tag FK)")
    private long tagSn;

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
