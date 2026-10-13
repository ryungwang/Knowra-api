package com.knowra.safety.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 신고. targetKind: POST | CMT | USER. stat: OPEN | RESOLVED | DISMISSED. */
@Entity
@Table(name = "TBL_REPORT", schema = "KNOWRA_USER")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "REPORT_SN") private Long reportSn;
    @Column(name = "REPORTER_SN", nullable = false) private long reporterSn;
    @Column(name = "TARGET_KIND", length = 10, nullable = false) private String targetKind;
    @Column(name = "TARGET_SN", nullable = false) private long targetSn;
    @Column(name = "REASON", length = 20, nullable = false) private String reason;
    @Column(name = "DETAIL", length = 500) private String detail;
    @Builder.Default @Column(name = "STAT", length = 10, nullable = false) private String stat = "OPEN";
    @Column(name = "HANDLED_BY") private Long handledBy;
    @Column(name = "HANDLED_AT") private LocalDateTime handledAt;
    @Builder.Default @Column(name = "FRST_CRT_DT", nullable = false, updatable = false) private LocalDateTime frstCrtDt = LocalDateTime.now();
}
