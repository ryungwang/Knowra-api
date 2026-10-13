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
        schema = "KNOWRA_POST",
        uniqueConstraints = @UniqueConstraint(name = "UK_POST_SAVE_USR", columnNames = {"USER_SN", "POST_SN"}),
        indexes = {
                @Index(name = "IDX_POST_SAVE_POST",    columnList = "POST_SN"),
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
    @Comment("게시글 SN (tbl_post 논리 참조)")
    private long postSn;

    @Column(name = "ACTVTN_YN", length = 1, nullable = false)
    @Comment("활성화 여부")
    @lombok.Builder.Default
    private String actvtnYn = "Y";

    /** 저장 폴더(없으면 NULL — 「전체」에만 보인다). */
    @Column(name = "FOLDER_SN")
    private Long folderSn;
}
