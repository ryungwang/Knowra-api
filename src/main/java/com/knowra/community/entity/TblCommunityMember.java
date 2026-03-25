package com.knowra.community.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "TBL_COMM_MBR",
        catalog = "KNOWRA_COMMUNITY",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_COMM_MBR_USR",
                columnNames = {"COMM_SN", "USER_SN"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblCommunityMember {

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

    @Column(name = "ACTVTN_YN", length = 1, nullable = false)
    @Comment("활성화 여부")
    @Builder.Default
    private String actvtnYn = "Y";

    @Column(name = "CREATR_SN", nullable = false, updatable = false)
    @Comment("생성자 SN")
    private long creatrSn;

    @Column(name = "FRST_CRT_DT", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    @Comment("최초 생성일시")
    @Builder.Default
    private LocalDateTime frstCrtDt = LocalDateTime.now();

    @Column(name = "MDFR_SN", insertable = false)
    @Comment("수정자 SN")
    private Long mdfrSn;

    @Column(name = "MDFCN_DT", columnDefinition = "DATETIME ON UPDATE CURRENT_TIMESTAMP")
    @Comment("수정일시")
    private LocalDateTime mdfcnDt;
}
