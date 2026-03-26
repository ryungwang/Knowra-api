package com.knowra.community.entity;

import com.knowra.common.entity.TblComFile;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "TBL_COMM", catalog = "KNOWRA_COMMUNITY")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblComm {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "COMM_SN")
  @Comment("커뮤니티 SN (PK)")
  private Long commSn;

  @Column(name = "COMM_NM", length = 320, nullable = false, unique = true)
  @Comment("슬러그 (URL 식별자)")
  private String commNm;

  @Column(name = "COMM_DSPL_NM", length = 100)
  @Comment("표시 이름")
  private String commDsplNm;

  @Column(name = "COMM_DESC", length = 320)
  @Comment("설명")
  private String commDesc;

  @Column(name = "CTGR_SN", nullable = false)
  @Comment("카테고리 SN (tbl_ctgr FK)")
  private long ctgrSn;

  @Column(name = "PRVCY_STNG", length = 20, nullable = false)
  @Comment("public / restricted / anonymous / private")
  private String prvcyStng;

  @Column(name = "LOGO_FILE_SN")
  @Comment("로고 이미지 (TBL_COM_FILE FK)")
  private Long logoFileSn;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "LOGO_FILE_SN", insertable = false, updatable = false)
  private TblComFile logoFile;

  @Column(name = "BNR_FILE_SN")
  @Comment("배너 이미지 (TBL_COM_FILE FK)")
  private Long bnrFileSn;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "BNR_FILE_SN", insertable = false, updatable = false)
  private TblComFile bnrFile;

  @OneToMany(fetch = FetchType.LAZY)
  @JoinColumn(name = "COMM_SN", insertable = false, updatable = false)
  private List<TblCommMbr> members;

  @Column(name = "MEMBER_CNT", nullable = false)
  @Comment("멤버 수 (캐시)")
  private long memberCnt = 0;

  @Column(name = "STAT", length = 1, nullable = false)
  @Comment("Y / N")
  private String stat = "Y";

  @Column(name = "ACTVTN_YN", length = 1, nullable = false)
  @Comment("활성화 여부")
  private String actvtnYn = "Y";

  @Column(name = "CREATR_SN", updatable = false, nullable = false)
  @Comment("생성자 SN")
  private long creatrSn;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Column(name = "FRST_CRT_DT", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", nullable = false, updatable = false)
  @Comment("최초 생성일시")
  private LocalDateTime frstCrtDt = LocalDateTime.now();

  @Column(name = "MDFR_SN", insertable = false)
  @Comment("수정자 SN")
  private Long mdfrSn;

  @Column(name = "MDFCN_DT", columnDefinition = "DATETIME ON UPDATE CURRENT_TIMESTAMP")
  @Comment("수정일시")
  private LocalDateTime mdfcnDt;
}
