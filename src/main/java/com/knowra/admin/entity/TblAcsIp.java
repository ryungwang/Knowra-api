package com.knowra.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_ACS_IP", catalog = "KNOWRA_CMS")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblAcsIp {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ACS_IP_SN", length = 22)
  @Comment("관리자접근일련번호")
  private Long acsIpSn;

  @Column(name = "IP_ADDR", nullable = false)
  @Comment("IP주소")
  private String ipAddr;

  @Column(name = "PLCUS_NM", nullable = false)
  @Comment("사용처명")
  private String plcusNm;

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
