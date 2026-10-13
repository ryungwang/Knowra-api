package com.knowra.community.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "TBL_COMM_MBR",
        schema = "KNOWRA_COMMUNITY",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_COMM_MBR_USR",
                columnNames = {"COMM_SN", "USER_SN"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblCommMbr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MBR_SN")
    @Comment("멤버 SN (PK)")
    private Long mbrSn;

    @Column(name = "COMM_SN", nullable = false)
    @Comment("커뮤니티 SN (tbl_comm FK)")
    private long commSn;

    @Column(name = "USER_SN", nullable = false)
    @Comment("사용자 SN (TBL_USER FK)")
    private long userSn;

    @Column(name = "ROLE", length = 20, nullable = false)
    @Comment("OWNER / ADMIN / MEMBER")
    @Builder.Default
    private String role = "MEMBER";

    @Column(name = "JOIN_TYP", length = 20, nullable = false)
    @Comment("APPLY / INVITE / AUTO")
    @Builder.Default
    private String joinTyp = "APPLY";

    @Column(name = "STAT", length = 20, nullable = false)
    @Comment("PENDING / ACTIVE / REJECTED / BANNED / WITHDRAWN")
    @Builder.Default
    private String stat = "ACTIVE";

    /** 새 글 알림 구독. 공지(NOTICE)는 이 값과 무관하게 전 멤버에게 간다. */
    @Column(name = "NOTIF_YN", length = 1, nullable = false)
    @Comment("새 글 알림 구독 여부")
    @Builder.Default
    private String notifYn = "N";

    @Column(name = "ACTVTN_YN", length = 1, nullable = false)
    @Comment("활성화 여부")
    @Builder.Default
    private String actvtnYn = "Y";

    @Column(name = "CREATR_SN", nullable = false, updatable = false)
    @Comment("생성자 SN")
    private long creatrSn;

    @Column(name = "FRST_CRT_DT", nullable = false, updatable = false)
    @Comment("최초 생성일시")
    @Builder.Default
    private LocalDateTime frstCrtDt = LocalDateTime.now();

    @Column(name = "MDFR_SN", insertable = false)
    @Comment("수정자 SN")
    private Long mdfrSn;

    @UpdateTimestamp
    @Column(name = "MDFCN_DT")
    @Comment("수정일시")
    private LocalDateTime mdfcnDt;

    /** 이 커뮤니티 화면을 마지막으로 연 시각 — 사이드바 「새 글」 수의 기준. */
    @Builder.Default
    @Column(name = "LAST_VISIT_AT", nullable = false)
    private LocalDateTime lastVisitAt = LocalDateTime.now();

    /** 참여할 때 규칙에 동의한 시각(규칙이 없던 때 참여했으면 NULL). */
    @Column(name = "RULES_AGREED_AT")
    private LocalDateTime rulesAgreedAt;
}
