package com.knowra.badge;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_USER_BADGE", schema = "KNOWRA_USER")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblUserBadge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "USER_BADGE_SN") private Long userBadgeSn;
    @Column(name = "USER_SN", nullable = false) private long userSn;
    @Column(name = "BADGE_KEY", nullable = false, length = 40) private String badgeKey;
    @Builder.Default @Column(name = "EARNED_AT", nullable = false) private LocalDateTime earnedAt = LocalDateTime.now();
}
