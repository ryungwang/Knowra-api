package com.knowra.post.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Table(
        name = "TBL_POST",
        catalog = "KNOWRA_POST",
        indexes = {
                @Index(name = "IDX_POST_USER",        columnList = "USER_SN"),
                @Index(name = "IDX_POST_FRST_CRT_DT", columnList = "FRST_CRT_DT")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TblPost extends BasePostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "POST_SN")
    @Comment("게시글 SN (PK)")
    private Long postSn;

    @Column(name = "POST_TYP", length = 10, nullable = false)
    @Comment("NORMAL / NOTICE")
    @Builder.Default
    private String postTyp = "NORMAL";
}
