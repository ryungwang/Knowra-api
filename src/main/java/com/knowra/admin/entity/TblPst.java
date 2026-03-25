package com.knowra.admin.entity;

import com.knowra.common.entity.TblComFile;
import com.knowra.user.entity.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "TBL_PST", catalog = "SCHM_CMS")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblPst {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "PST_SN", length = 22)
  @Comment("게시물일련번호")
  private Long pstSn;

  @Column(name = "BBS_SN", length = 22, nullable = false)
  @Comment("게시판일련번호")
  private long bbsSn;

  @Column(name = "PST_CLSF", length = 22)
  @Comment("게시물분류")
  private Long pstClsf;

  @Column(name = "UPEND_NTC_YN", nullable = false)
  @Comment("상단공지여부")
  private String upendNtcYn = "N";

  @Column(name = "START_DT")
  @Comment("공지시작일")
  private String startDt;

  @Column(name = "END_DT")
  @Comment("공지종료일")
  private String endDt;

  @Column(name = "PST_TTL", length = 256, nullable = false)
  @Comment("게시글제목")
  private String pstTtl;

  @Column(name = "PST_CN", columnDefinition = "LONGTEXT", nullable = false)
  @Comment("게시글내용")
  private String pstCn;

  @Column(name = "PST_INQ_CNT", length = 10, columnDefinition = "INT(10) DEFAULT 0")
  @Comment("조회수")
  private long pstInqCnt = 0;

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
  private TblUser tblUser;

//  @Transient
//  private TblBbs tblBbs;

  @Transient
  private List<TblComFile> tblComFiles;
}
