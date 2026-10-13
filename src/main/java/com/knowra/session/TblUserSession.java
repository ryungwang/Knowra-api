package com.knowra.session;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_USER_SESSION", schema = "KNOWRA_USER")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblUserSession {
    @Id @Column(name = "SID", length = 36) private String sid;
    @Column(name = "USER_SN", nullable = false) private long userSn;
    @Column(name = "USER_AGENT", length = 300) private String userAgent;
    @Column(name = "IP", length = 64) private String ip;
    @Builder.Default @Column(name = "CREATED_AT", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default @Column(name = "LAST_SEEN_AT", nullable = false) private LocalDateTime lastSeenAt = LocalDateTime.now();
}
