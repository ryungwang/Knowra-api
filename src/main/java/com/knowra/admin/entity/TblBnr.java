package com.knowra.admin.entity;

import com.knowra.common.entity.TblComFile;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_BNR", catalog = "KNOWRA_CMS")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblBnr {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "BNR_SN", length = 22)
  @Comment("배너일련번호")
  private Long bnrSn;

  @Column(name = "BNR_KND", nullable = false)
  @Comment("배너종류")
  private String bnrKnd;

  @Column(name = "BNR_TTL", nullable = false)
  @Comment("배너제목")
  private String bnrTtl;

  @Column(name = "BNR_URL_ADDR", nullable = false)
  @Comment("배너URL주소")
  private String bnrUrlAddr;

  @Column(name = "START_DT", nullable = false)
  @Comment("시작일")
  private String startDt;

  @Column(name = "END_DT", nullable = false)
  @Comment("종료일")
  private String endDt;

  @Column(name = "ACTVTN_YN", nullable = false)
  @Comment("활성여부")
  private String actvtnYn = "Y";

  @Column(name = "CREATR_SN", updatable=false, nullable = false)
  @Comment("생성자일련번호")
  private long creatrSn;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Column(name = "FRST_CRT_DT", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", nullable = false, updatable = false)
  @Comment("최초생성일시")
  private LocalDateTime frstCrtDt = LocalDateTime.now();

  @Column(name = "MDFR_SN", insertable = false)
  @Comment("수정자일련번호")
  private Long mdfrSn;

  @Column(name = "MDFCN_DT", columnDefinition = "DATETIME ON UPDATE CURRENT_TIMESTAMP")
  @Comment("수정일")
  private LocalDateTime mdfcnDt;

  @Transient
  private Long delFileSn;

  @Transient
  private TblComFile tblComFile;
}
