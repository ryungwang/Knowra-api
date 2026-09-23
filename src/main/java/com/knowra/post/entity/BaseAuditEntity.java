package com.knowra.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@MappedSuperclass
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
public abstract class BaseAuditEntity {

    @Column(name = "CREATR_SN", nullable = false, updatable = false)
    @Comment("생성자 SN")
    private long creatrSn;

    @Column(name = "FRST_CRT_DT", nullable = false, updatable = false)
    @Comment("최초 생성일시")
    @Builder.Default
    private LocalDateTime frstCrtDt = LocalDateTime.now();

    @Column(name = "MDFR_SN", insertable = false)
    @Comment("수정자 SN")
    private Long mdfrSn;

    @UpdateTimestamp
    @Column(name = "MDFCN_DT")
    @Comment("수정일시")
    private LocalDateTime mdfcnDt;
}
