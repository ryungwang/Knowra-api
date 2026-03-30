package com.knowra.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_USER_TAG", catalog = "KNOWRA_USER")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblUserTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_TAG_SN" , length = 22)
    @Comment("사용자 태그 SN (PK)")
    private Long userTagSn;

    @Column(name = "USER_SN" , length = 22)
    @Comment("사용자일련번호")
    private long userSn;

    @Column(name = "TAG_SN", nullable = false)
    @Comment("태그 SN (tbl_tag FK)")
    private long tagSn;

    @Column(name = "USE_COUNT", nullable = false)
    @Comment("사용횟수")
    private long useCount;

    @Column(name = "ACTVTN_YN", columnDefinition = "CHAR(1) DEFAULT 'Y'")
    @Comment("활성여부")
    @Builder.Default
    private String actvtnYn = "Y";

    @Column(name = "CREATR_SN", columnDefinition = "INT(10)", updatable=false, nullable = false)
    @Comment("생성자일련번호")
    @Builder.Default
    private long creatrSn = 1;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Column(name = "FRST_CRT_DT", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", updatable = false)
    @Comment("최초생성일시")
    @Builder.Default
    private LocalDateTime frstCrtDt = LocalDateTime.now();
}
