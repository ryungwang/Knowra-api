package com.knowra.poll.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TBL_POLL_OPTION", schema = "KNOWRA_POST")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblPollOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "OPTION_SN") private Long optionSn;
    @Column(name = "POLL_SN", nullable = false) private long pollSn;
    @Column(name = "ORD", nullable = false) private int ord;
    @Column(name = "LABEL", nullable = false, length = 80) private String label;
    @Builder.Default @Column(name = "VOTE_CNT", nullable = false) private int voteCnt = 0;
}
