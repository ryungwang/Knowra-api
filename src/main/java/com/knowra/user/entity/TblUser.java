package com.knowra.user.entity.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_USER", catalog = "SCHM_USER")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblUser {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "USER_SN", length = 22)
  @Comment("사용자일련번호")
  private Long userSn;

  @Column(name = "EMAIL", length = 320, nullable = false)
  @Comment("사용자이메일")
  private String email;

  @Column(name = "PASSWORD", length = 200, nullable = false)
  @Comment("비밀번호")
  private String password;

  @Column(name = "NAME", length = 100, nullable = false)
  @Comment("이름")
  private String name;

  @Column(name = "JOIN_YMD", nullable = false, updatable = false)
  @Comment("가입일시")
  private LocalDateTime joinYmd = LocalDateTime.now();

  @Column(name = "LGN_FAIL_NMTM", insertable = false, nullable = false)
  @Comment("로그인실패횟수")
  private long lgnFailNmtm = 0;

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
