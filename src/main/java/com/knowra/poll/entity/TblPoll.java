package com.knowra.poll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_POLL", schema = "KNOWRA_POST")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblPoll {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "POLL_SN") private Long pollSn;
    @Column(name = "POST_SN", nullable = false) private long postSn;
    @Builder.Default @Column(name = "MULTI_YN", nullable = false, length = 1) private String multiYn = "N";
    @Column(name = "ENDS_AT", nullable = false) private LocalDateTime endsAt;
    @Builder.Default @Column(name = "FRST_CRT_DT", nullable = false, updatable = false) private LocalDateTime frstCrtDt = LocalDateTime.now();
}
