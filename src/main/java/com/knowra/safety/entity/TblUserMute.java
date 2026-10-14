package com.knowra.safety.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 사용자 숨기기(V36) — 한쪽만. 차단과 달리 상대는 모르고 DM·알림 연결은 그대로다(숨긴 사람의 반응 알림만 안 온다). */
@Entity
@Table(name = "TBL_USER_MUTE", schema = "KNOWRA_USER")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblUserMute {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "MUTE_SN") private Long muteSn;
    @Column(name = "USER_SN", nullable = false) private long userSn;
    @Column(name = "MUTED_SN", nullable = false) private long mutedSn;
    @Builder.Default @Column(name = "CREATED_AT", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
