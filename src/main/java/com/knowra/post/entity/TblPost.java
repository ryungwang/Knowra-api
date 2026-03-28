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
}
