package com.knowra.event;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_COMM_EVENT_RSVP", schema = "KNOWRA_COMMUNITY")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblCommEventRsvp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "RSVP_SN") private Long rsvpSn;
    @Column(name = "EVENT_SN", nullable = false) private long eventSn;
    @Column(name = "USER_SN", nullable = false) private long userSn;
    @Builder.Default @Column(name = "FRST_CRT_DT", nullable = false, updatable = false) private LocalDateTime frstCrtDt = LocalDateTime.now();
}
