package com.knowra.community.entity;

import com.knowra.common.entity.BaseCmtEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Table(
        name = "TBL_COMM_POST_CMT",
        catalog = "KNOWRA_COMMUNITY",
        indexes = {
                @Index(name = "IDX_COMM_POST_CMT_POST", columnList = "COMM_POST_SN"),
                @Index(name = "IDX_COMM_POST_CMT_PRNT", columnList = "PRNT_CMT_SN")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TblCommPostCmt extends BaseCmtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMM_POST_CMT_SN")
    @Comment("댓글 SN (PK)")
    private Long commPostCmtSn;

    @Column(name = "COMM_POST_SN", nullable = false)
    @Comment("커뮤니티 게시글 SN (tbl_comm_post FK)")
    private long commPostSn;
}
