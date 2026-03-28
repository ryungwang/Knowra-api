package com.knowra.post.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Table(
        name = "TBL_POST_CMT",
        catalog = "KNOWRA_POST",
        indexes = {
                @Index(name = "IDX_POST_CMT_POST", columnList = "POST_SN"),
                @Index(name = "IDX_POST_CMT_PRNT", columnList = "PRNT_CMT_SN")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TblPostCmt extends BaseCmtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "POST_CMT_SN")
    @Comment("댓글 SN (PK)")
    private Long postCmtSn;

    @Column(name = "POST_SN", nullable = false)
    @Comment("게시글 SN (tbl_post FK)")
    private long postSn;
}
