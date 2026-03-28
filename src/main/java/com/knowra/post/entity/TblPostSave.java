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
        name = "TBL_POST_SAVE",
        catalog = "KNOWRA_POST",
        uniqueConstraints = @UniqueConstraint(name = "UK_POST_SAVE_USR", columnNames = {"USER_SN", "POST_TYP", "POST_SN"}),
        indexes = {
                @Index(name = "IDX_POST_SAVE_POST",    columnList = "POST_TYP, POST_SN"),
                @Index(name = "IDX_POST_SAVE_USER_DT", columnList = "USER_SN, FRST_CRT_DT")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TblPostSave extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "POST_SAVE_SN")
    @Comment("저장 SN (PK)")
    private Long postSaveSn;

    @Column(name = "USER_SN", nullable = false)
    @Comment("사용자 SN (TBL_USER FK)")
    private long userSn;

    @Column(name = "POST_SN", nullable = false)
    @Comment("게시글 SN (POST_TYP에 따라 tbl_post 또는 tbl_comm_post 논리 참조)")
    private long postSn;

    @Column(name = "POST_TYP", length = 10, nullable = false)
    @Comment("게시글 유형: POST / COMM")
    private String postTyp;

    @Column(name = "ACTVTN_YN", length = 1, nullable = false)
    @Comment("활성화 여부")
    @lombok.Builder.Default
    private String actvtnYn = "Y";
}
