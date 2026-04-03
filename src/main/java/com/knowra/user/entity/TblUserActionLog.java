package com.knowra.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "TBL_USER_ACTION_LOG",
    catalog = "KNOWRA_USER",
    indexes = {
        @Index(name = "idx_action_user_target", columnList = "USER_SN, TARGET_TYPE, TARGET_SN"),
        @Index(name = "idx_action_reg_dt",      columnList = "REG_DT")
    }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblUserActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ACTION_SN")
    @Comment("행동 로그 SN (PK)")
    private Long actionSn;

    @Column(name = "USER_SN", nullable = false)
    @Comment("행동 주체 유저 SN")
    private long userSn;

    @Column(name = "TARGET_TYPE", length = 20, nullable = false)
    @Comment("COMM | COMM_POST | POST | USER")
    private String targetType;

    @Column(name = "TARGET_SN", nullable = false)
    @Comment("대상 SN")
    private long targetSn;

    @Column(name = "ACTION_TYPE", length = 20, nullable = false)
    @Comment("VIEW | LIKE | COMMENT | SCRAP | POST | JOIN | FOLLOW")
    private String actionType;

    @Column(name = "REG_DT", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    @Comment("행동 발생 일시")
    @Builder.Default
    private LocalDateTime regDt = LocalDateTime.now();

    // ── 타입 상수 ──────────────────────────────────────────────────
    public static final String TARGET_COMM      = "COMM";
    public static final String TARGET_COMM_POST = "COMM_POST";
    public static final String TARGET_POST      = "POST";
    public static final String TARGET_USER      = "USER";

    public static final String ACTION_VIEW    = "VIEW";
    public static final String ACTION_LIKE    = "LIKE";
    public static final String ACTION_COMMENT = "COMMENT";
    public static final String ACTION_SCRAP   = "SCRAP";
    public static final String ACTION_POST    = "POST";
    public static final String ACTION_JOIN    = "JOIN";
    public static final String ACTION_FOLLOW  = "FOLLOW";
}
