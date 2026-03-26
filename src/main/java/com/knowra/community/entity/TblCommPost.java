package com.knowra.community.entity;

import com.knowra.common.entity.BasePostEntity;
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
        name = "TBL_COMM_POST",
        catalog = "KNOWRA_COMMUNITY",
        indexes = {
                @Index(name = "IDX_COMM_POST_COMM",        columnList = "COMM_SN"),
                @Index(name = "IDX_COMM_POST_USER",        columnList = "USER_SN"),
                @Index(name = "IDX_COMM_POST_FRST_CRT_DT", columnList = "FRST_CRT_DT")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TblCommPost extends BasePostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMM_POST_SN")
    @Comment("커뮤니티 게시글 SN (PK)")
    private Long commPostSn;

    @Column(name = "COMM_SN", nullable = false)
    @Comment("커뮤니티 SN (tbl_comm FK)")
    private long commSn;

    @Column(name = "POST_TYP", length = 10, nullable = false)
    @Comment("NORMAL / NOTICE")
    @Builder.Default
    private String postTyp = "NORMAL";
}
