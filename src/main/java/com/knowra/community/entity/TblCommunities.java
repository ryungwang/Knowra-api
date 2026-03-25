package com.knowra.community.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_COMMUNITIES", catalog = "KNOWRA_COMMUNITY")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblCommunities {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "COMMUNITY_SN", length = 22)
  @Comment("커뮤니티 일련번호")
  private Long communitySn;

  @Column(name = "COMMUNITY_NM", length = 320, nullable = false)
  @Comment("커뮤니티 이름")
  private String communityNm;

  @Column(name = "COMMUNITY_DESCRIPTION", length = 320, nullable = false)
  @Comment("커뮤니티 소개")
  private String communityDescription;

  @Column(name = "CATEGORY", length = 22, nullable = false)
  @Comment("커뮤니티 주제")
  private long category;

  @Column(name = "PRIVACY_SETTINGS", length = 22, nullable = false)
  @Comment("커뮤니티 유형")
  private String privacySettings;

  @Column(name = "STATUS", length = 20, nullable = false)
  @Comment("상태")
  private String status = "Y";

  @Column(name = "ACTVTN_YN", nullable = false)
  @Comment("활성여부")
  private String actvtnYn = "Y";

  @Column(name = "CREATR_SN", updatable=false, nullable = false)
  @Comment("생성자일련번호")
  private long creatrSn = 1;

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


}
