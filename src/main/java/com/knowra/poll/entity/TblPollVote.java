package com.knowra.poll.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_POLL_VOTE", schema = "KNOWRA_POST")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblPollVote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "VOTE_SN") private Long voteSn;
    @Column(name = "POLL_SN", nullable = false) private long pollSn;
    @Column(name = "OPTION_SN", nullable = false) private long optionSn;
    @Column(name = "USER_SN", nullable = false) private long userSn;
    @Builder.Default @Column(name = "FRST_CRT_DT", nullable = false, updatable = false) private LocalDateTime frstCrtDt = LocalDateTime.now();
}
