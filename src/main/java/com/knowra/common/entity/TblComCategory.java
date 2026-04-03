package com.knowra.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_COM_CATEGORY", catalog = "KNOWRA_COM")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblComCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CATEGORY_SN", length = 22)
    @Comment("카테고리 시퀀스 (PK)")
    private Long categorySn;

    @Column(name = "CATEGORY_NM", length = 100, nullable = false, unique = true)
    @Comment("카테고리명 (예: IT, 게임)")
    private String categoryNm;

    @Column(name = "CATEGORY_ICON", length = 50)
    @Comment("이모지 아이콘")
    private String categoryIcon;

    @Column(name = "SORT_ORDER", nullable = false)
    @Comment("정렬 순서")
    private int sortOrder = 0;

    @Column(name = "ACTVTN_YN", length = 1, nullable = false)
    @Comment("활성여부")
    private String actvtnYn = "Y";

    @Column(name = "CREATR_SN", nullable = false, updatable = false)
    @Comment("생성자일련번호")
    private long creatrSn;

    @Column(name = "FRST_CRT_DT", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", nullable = false, updatable = false)
    @Comment("최초생성일시")
    private LocalDateTime frstCrtDt = LocalDateTime.now();

    @Column(name = "MDFR_SN", insertable = false)
    @Comment("수정자일련번호")
    private Long mdfrSn;

    @Column(name = "MDFCN_DT", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @Comment("수정일")
    private LocalDateTime mdfcnDt;
}
