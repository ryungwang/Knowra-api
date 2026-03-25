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
        name = "TBL_COMMUNITY_MEMBERS",
        catalog = "KNOWRA_COMMUNITY",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_COMMUNITY_USER",
                columnNames = {"COMMUNITY_SN", "USER_SN"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblCommunityMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MEMBER_SN")
    @Comment("멤버 일련번호")
    private Integer memberSn;

    @Column(name = "COMMUNITY_SN", nullable = false)
    @Comment("커뮤니티 일련번호")
    private Integer communitySn;

    @Column(name = "USER_SN", nullable = false)
    @Comment("유저 일련번호")
    private Integer userSn;

    @Column(name = "ROLE", length = 20, nullable = false)
    @Comment("역할 (OWNER, ADMIN, MEMBER)")
    @Builder.Default
    private String role = "MEMBER";

    @Column(name = "JOIN_DT", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    @Comment("가입일시")
    @Builder.Default
    private LocalDateTime joinDt = LocalDateTime.now();

    @Column(name = "JOIN_TYPE", length = 20, nullable = false)
    @Comment("가입방법 (APPLY, INVITE, AUTO)")
    @Builder.Default
    private String joinType = "APPLY";

    @Column(name = "STATUS", length = 20, nullable = false)
    @Comment("상태")
    @Builder.Default
    private String status = "Y";

    @Column(name = "ACTVTN_YN", length = 1, nullable = false)
    @Comment("활성여부")
    @Builder.Default
    private String actvtnYn = "Y";

    @Column(name = "CREATR_SN", nullable = false, updatable = false)
    @Comment("생성자 일련번호")
    private Integer creatrSn;

    @Column(name = "FRST_CRT_DT", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    @Comment("최초생성일시")
    @Builder.Default
    private LocalDateTime frstCrtDt = LocalDateTime.now();

    @Column(name = "MDFR_SN", insertable = false)
    @Comment("수정자 일련번호")
    private Integer mdfrSn;

    @Column(name = "MDFCN_DT", columnDefinition = "DATETIME ON UPDATE CURRENT_TIMESTAMP")
    @Comment("수정일")
    private LocalDateTime mdfcnDt;
}