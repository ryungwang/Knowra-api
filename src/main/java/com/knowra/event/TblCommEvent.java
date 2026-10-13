package com.knowra.event;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_COMM_EVENT", schema = "KNOWRA_COMMUNITY")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblCommEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "EVENT_SN") private Long eventSn;
    @Column(name = "COMM_SN", nullable = false) private long commSn;
    @Column(name = "CREATED_BY", nullable = false) private long createdBy;
    @Column(name = "TITLE", nullable = false, length = 80) private String title;
    @Builder.Default @Column(name = "DESCR", nullable = false, length = 2000) private String descr = "";
    @Column(name = "STARTS_AT", nullable = false) private LocalDateTime startsAt;
    @Column(name = "ENDS_AT") private LocalDateTime endsAt;
    @Column(name = "PLACE", length = 120) private String place;
    @Column(name = "ONLINE_URL", length = 300) private String onlineUrl;
    @Column(name = "CAPACITY") private Integer capacity;
    @Builder.Default @Column(name = "STAT", nullable = false, length = 10) private String stat = "ACTIVE";
    @Builder.Default @Column(name = "REMINDED_DAY", nullable = false) private boolean remindedDay = false;
    @Builder.Default @Column(name = "REMINDED_HOUR", nullable = false) private boolean remindedHour = false;
    @Builder.Default @Column(name = "FRST_CRT_DT", nullable = false, updatable = false) private LocalDateTime frstCrtDt = LocalDateTime.now();
}
