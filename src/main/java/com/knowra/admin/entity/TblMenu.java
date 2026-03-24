package com.knowra.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_MENU", catalog = "SCHM_CMS")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblMenu {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "MENU_SN", length = 22)
  @Comment("메뉴일련번호")
  private Long menuSn;

  @Column(name = "MENU_TYPE", nullable = false)
  @Comment("메뉴유형")
  private String menuType;

  @Column(name = "BBS_SN")
  @Comment("게시판일련번호")
  private Long bbsSn;

  @Column(name = "MENU_NAME", length = 200, nullable = false)
  @Comment("메뉴명")
  private String menuName;

  @Column(name = "MENU_SORT_ORDER", length = 10, nullable = false)
  @Comment("메뉴정렬")
  private long menuSortOrder;

  @Column(name = "MENU_PATH", length = 300, nullable = false)
  @Comment("메뉴경로")
  private String menuPath;

  @Column(name = "APLCN_NTN_LTR", length = 20, nullable = false)
  @Comment("적용국가문자")
  private String aplcnNtnLtr = "EN";

  @Column(name = "MENU_NM_PATH", length = 300)
  @Comment("메뉴이름경로")
  private String menuNmPath;

  @Column(name = "MENU_SN_PATH", length = 300)
  @Comment("메뉴일련번호경로")
  private String menuSnPath;

  @Column(name = "MENU_WHOL_PATH", length = 300)
  @Comment("메뉴전체경로")
  private String menuWholPath;

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
}
