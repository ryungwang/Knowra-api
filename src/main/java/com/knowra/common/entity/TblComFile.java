package com.knowra.common.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_COM_FILE", schema = "KNOWRA_COM")
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

    @Column(name = "ATCH_FILE_PATH_NM", length = 300, nullable = false)
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

    @Column(name = "ACTVTN_YN")
    @Comment("활성여부")
    private String actvtnYn = "Y";

    @Column(name = "CREATR_SN", updatable=false, nullable = false)
    @Comment("생성자일련번호")
    private long creatrSn;

    @Column(name = "FRST_CRT_DT")
    @Comment("최초생성일시")
    private LocalDateTime frstCrtDt = LocalDateTime.now();

    @Column(name = "MDFR_SN", insertable = false)
    @Comment("수정자일련번호")
    private Long mdfrSn;

    @UpdateTimestamp
    @Column(name = "MDFCN_DT")
    @Comment("수정일")
    private LocalDateTime mdfcnDt;

    /** 저장소 객체 키(V22). 있으면 공개 URL 은 저장소가 만든다. 없으면 예전 /upload 파일. */
    @Column(name = "FILE_KEY", length = 300)
    private String fileKey;

    /**
     * 화면이 쓰는 주소. 새 파일은 저장소의 공개 URL(운영은 S3, 절대 주소), 예전 파일은 {@code /upload/...}
     * 상대 경로 — 화면은 절대 주소면 그대로, 상대면 API 주소를 붙인다(fileSrc).
     */
    @jakarta.persistence.Transient
    public String getUrl() {
        if (fileKey != null) return com.knowra.cmm.storage.StorageUrls.publicUrl(fileKey);
        return atchFilePathNm == null ? null : atchFilePathNm + "/" + strgFileNm + "." + atchFileExtnNm;
    }
}
