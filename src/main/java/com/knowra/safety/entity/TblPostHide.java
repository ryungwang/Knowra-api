package com.knowra.safety.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_POST_HIDE", schema = "KNOWRA_POST")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblPostHide {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "HIDE_SN") private Long hideSn;
    @Column(name = "USER_SN", nullable = false) private long userSn;
    @Column(name = "POST_SN", nullable = false) private long postSn;
    @Builder.Default @Column(name = "FRST_CRT_DT", nullable = false, updatable = false) private LocalDateTime frstCrtDt = LocalDateTime.now();
}
