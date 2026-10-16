package com.knowra.community.entity;

import com.knowra.common.entity.TblComFile;
import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "TBL_COMM", schema = "KNOWRA_COMMUNITY")
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

  /**
   * ⚠️ 응답에 싣지 않는다. 커뮤니티 정보(getCommunity)가 이 엔티티를 그대로 내보내서, 로그인 없이도
   * 비공개 커뮤니티의 멤버 명단(사용자 번호·역할·가입 대기·차단 여부·마지막 방문)이 보였다.
   * 인원수는 memberCnt, 명단은 권한을 보는 getMemberList 로.
   */
  @com.fasterxml.jackson.annotation.JsonIgnore
  @OneToMany(fetch = FetchType.LAZY)
  @JoinColumn(name = "COMM_SN", insertable = false, updatable = false)
  private List<TblCommMbr> members;

  @Column(name = "MEMBER_CNT", nullable = false)
  @Comment("멤버 수 (캐시)")
  private long memberCnt = 0;

  /** 닫힌 시각(NULL = 열림). 바꾸는 건 CommunityService.setClosed 만 — 만들기·설정 저장 본문으로 못 바꾸게 읽기 전용. V52. */
  @Column(name = "CLOSED_AT", insertable = false, updatable = false)
  private LocalDateTime closedAt;

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
  @Column(name = "FRST_CRT_DT", nullable = false, updatable = false)
  @Comment("최초 생성일시")
  private LocalDateTime frstCrtDt = LocalDateTime.now();

  @Column(name = "MDFR_SN", insertable = false)
  @Comment("수정자 SN")
  private Long mdfrSn;

  @UpdateTimestamp
  @Column(name = "MDFCN_DT")
  @Comment("수정일시")
  private LocalDateTime mdfcnDt;
}
