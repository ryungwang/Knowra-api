package com.knowra.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_COM_FILE", catalog = "SCHM_COM")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblComFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ATCH_FILE_SN", length = 22)
    @Comment("파일일련번호")
    private Long atchFileSn;

    @Column(name = "STRG_FILE_NM", length = 100, nullable = false)
    @Comment("파일UUID")
    private String strgFileNm;

    @Column(name = "ATCH_FILE_NM", length = 100, nullable = false)
    @Comment("첨부파일명")
    private String atchFileNm;

    @Column(name = "ATCH_FILE_PATH_NM", length = 100, nullable = false)
    @Comment("첨부파일경로")
    private String atchFilePathNm;

    @Column(name = "ATCH_FILE_SZ", length = 10, nullable = false)
    @Comment("첨부파일사이즈")
    private long atchFileSz;

    @Column(name = "ATCH_FILE_EXTN_NM", length = 20, nullable = false)
    @Comment("첨부파일확장자")
    private String atchFileExtnNm;

    @Column(name = "PSN_TBL_SN", length = 40, nullable = false)
    @Comment("소유테이블데이터기본키")
    private String psnTblSn;

    @Column(name = "ACTVTN_YN", columnDefinition = "CHAR(1) DEFAULT 'Y'")
    @Comment("활성여부")
    private String actvtnYn = "Y";

    @Column(name = "CREATR_SN", columnDefinition = "INT(10)", updatable=false, nullable = false)
    @Comment("생성자일련번호")
    private long creatrSn;

    @Column(name = "FRST_CRT_DT", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    @Comment("최초생성일시")
    private LocalDateTime frstCrtDt = LocalDateTime.now();

    @Column(name = "MDFR_SN", columnDefinition = "INT(10)", insertable = false)
    @Comment("수정자일련번호")
    private Long mdfrSn;

    @Column(name = "MDFCN_DT", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    @Comment("수정일")
    private LocalDateTime mdfcnDt;
}
