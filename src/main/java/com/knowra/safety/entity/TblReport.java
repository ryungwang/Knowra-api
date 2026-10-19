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
    /** 신고한 회원. null = 자동 검수(AI)가 올린 신고(V63) */
    @Column(name = "REPORTER_SN") private Long reporterSn;
    @Column(name = "TARGET_KIND", length = 10, nullable = false) private String targetKind;
    @Column(name = "TARGET_SN", nullable = false) private long targetSn;
    @Column(name = "REASON", length = 20, nullable = false) private String reason;
    @Column(name = "DETAIL", length = 500) private String detail;
    @Builder.Default @Column(name = "STAT", length = 10, nullable = false) private String stat = "OPEN";
    @Column(name = "HANDLED_BY") private Long handledBy;
    @Column(name = "HANDLED_AT") private LocalDateTime handledAt;
    /** 글·댓글이 속한 커뮤니티 — 그 운영진의 신고함에 뜬다(V28). 운영진 처리는 사이트 관리자 처리(stat)와 따로다 */
    @Column(name = "COMM_SN") private Long commSn;
    @Column(name = "COMM_STAT", length = 10) private String commStat;
    @Column(name = "COMM_HANDLED_BY") private Long commHandledBy;
    @Column(name = "COMM_HANDLED_AT") private LocalDateTime commHandledAt;
    /** 대상 언어 — ko·ja·zh·latn·und(V69). 검수자를 언어로 나눈다 */
    @Column(name = "LANG", length = 10) private String lang;
    @Builder.Default @Column(name = "FRST_CRT_DT", nullable = false, updatable = false) private LocalDateTime frstCrtDt = LocalDateTime.now();
}
