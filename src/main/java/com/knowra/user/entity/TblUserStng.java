package com.knowra.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_USER_STNG", catalog = "KNOWRA_USER")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblUserStng {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "USER_STNG_SN", length = 22)
  @Comment("개인 설정 SN (PK)")
  private Long userStngSn;

  @Column(name = "USER_SN", length = 22)
  @Comment("사용자일련번호")
  private long userSn;

  @Column(name = "THEME_TYP", length = 20, nullable = false)
  @Comment("테마 유형 (light / dark / system)")
  private String themeTyp = "system";

  @Column(name = "CMT_NTFCTN_YN", length = 1, nullable = false)
  @Comment("댓글 알림 여부 (Y / N)")
  private String cmtNtfctnYn = "Y";

  @Column(name = "FLWR_NTFCTN_YN", length = 1, nullable = false)
  @Comment("팔로우 알림 여부 (Y / N)")
  private String flwrNtfctnYn = "Y";

  @Column(name = "LIKE_NTFCTN_YN", length = 1, nullable = false)
  @Comment("좋아요 알림 여부 (Y / N)")
  private String likeNtfctnYn = "N";

  @Column(name = "SYS_NTFCTN_YN", length = 1, nullable = false)
  @Comment("시스템 알림 여부 (Y / N)")
  private String sysNtfctnYn = "Y";

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
