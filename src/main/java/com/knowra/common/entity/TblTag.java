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
        name = "TBL_TAG",
        catalog = "KNOWRA_COM",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_TAG_NM",
                columnNames = {"TAG_NM"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TAG_SN")
    @Comment("태그 SN (PK)")
    private Long tagSn;

    @Column(name = "TAG_NM", length = 100, nullable = false)
    @Comment("태그명 (예: #Python)")
    private String tagNm;

    @Column(name = "USE_COUNT", nullable = false)
    @Comment("사용횟수")
    private long useCount;

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
