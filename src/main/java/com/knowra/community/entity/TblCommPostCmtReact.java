package com.knowra.community.entity;

import com.knowra.post.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Table(
        name = "TBL_COMM_POST_CMT_REACT",
        catalog = "KNOWRA_COMMUNITY",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_CMT_REACT",
                columnNames = {"COMM_POST_CMT_SN", "USER_SN"}
        )
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TblCommPostCmtReact extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CMT_REACT_SN")
    @Comment("반응 SN (PK)")
    private Long cmtReactSn;

    @Column(name = "COMM_POST_CMT_SN", nullable = false)
    @Comment("댓글 SN (tbl_comm_post_cmt FK)")
    private long commPostCmtSn;

    @Column(name = "USER_SN", nullable = false)
    @Comment("사용자 SN (TBL_USER FK)")
    private long userSn;

    @Column(name = "REACT_TYP", length = 10, nullable = false)
    @Comment("LIKE / LOVE / HAHA / WOW / SAD / ANGRY")
    private String reactTyp;

    @Column(name = "ACTVTN_YN", length = 1, nullable = false)
    @Builder.Default
    @Comment("활성화 여부")
    private String actvtnYn = "Y";
}
