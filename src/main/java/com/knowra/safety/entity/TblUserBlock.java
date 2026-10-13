package com.knowra.safety.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_USER_BLOCK", schema = "KNOWRA_USER")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblUserBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "BLOCK_SN") private Long blockSn;
    @Column(name = "USER_SN", nullable = false) private long userSn;
    @Column(name = "BLOCKED_SN", nullable = false) private long blockedSn;
    @Builder.Default @Column(name = "FRST_CRT_DT", nullable = false, updatable = false) private LocalDateTime frstCrtDt = LocalDateTime.now();
}
