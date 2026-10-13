package com.knowra.push.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_PUSH_SUB", schema = "KNOWRA_USER")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblPushSub {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "SUB_SN") private Long subSn;
    @Column(name = "USER_SN", nullable = false) private long userSn;
    @Column(name = "ENDPOINT", nullable = false, length = 1000) private String endpoint;
    @Column(name = "P256DH", nullable = false, length = 200) private String p256dh;
    @Column(name = "AUTH", nullable = false, length = 100) private String auth;
    @Column(name = "USER_AGENT", length = 300) private String userAgent;
    @Builder.Default @Column(name = "FRST_CRT_DT", nullable = false, updatable = false) private LocalDateTime frstCrtDt = LocalDateTime.now();
    @Column(name = "LAST_OK_DT") private LocalDateTime lastOkDt;
}
