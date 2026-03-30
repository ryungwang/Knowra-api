package com.knowra.user.entity;

import com.knowra.post.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Table(
        name = "TBL_USER_FLWR",
        catalog = "KNOWRA_USER",
        uniqueConstraints = @UniqueConstraint(name = "UK_USR_FLWR_REL", columnNames = {"FLWR_USER_SN", "FLWNG_USER_SN"})
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TblUserFlwr extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FLWR_SN")
    @Comment("팔로우 SN (PK)")
    private Long flwrSn;

    @Column(name = "FLWR_USER_SN", nullable = false)
    @Comment("팔로우 하는 사용자 SN (follower)")
    private long flwrUserSn;

    @Column(name = "FLWNG_USER_SN", nullable = false)
    @Comment("팔로우 받는 사용자 SN (followee)")
    private long flwngUserSn;

    @Column(name = "ACTVTN_YN", length = 1, nullable = false)
    @Comment("활성화 여부 (Y: 팔로우 중 / N: 언팔로우)")
    @lombok.Builder.Default
    private String actvtnYn = "Y";
}
