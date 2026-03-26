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
        name = "TBL_COMM_POST_TAG",
        catalog = "KNOWRA_COMMUNITY",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_COMM_POST_TAG",
                columnNames = {"COMM_POST_SN", "TAG_SN"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblCommPostTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMM_POST_TAG_SN")
    @Comment("커뮤니티 게시글-태그 SN (PK)")
    private Long commPostTagSn;

    @Column(name = "COMM_POST_SN", nullable = false)
    @Comment("커뮤니티 게시글 SN (tbl_comm_post FK)")
    private long commPostSn;

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
