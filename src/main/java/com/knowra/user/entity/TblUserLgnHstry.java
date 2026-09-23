package com.knowra.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_USER_LGN_HSTRY", schema = "KNOWRA_USER")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TblUserLgnHstry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LGN_HSTRY_SN" , length = 22)
    @Comment("사용자일련번호")
    private Long lgnHstrySn;

    @Column(name = "USER_SN" , length = 22)
    @Comment("사용자일련번호")
    private long userSn;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Column(name = "LGN_DT", updatable = false)
    @Comment("로그인일시")
    private LocalDateTime lgnDt = LocalDateTime.now();

    @Column(name = "LGN_IP", nullable = false, updatable = false)
    @Comment("활성여부")
    private String lgnIp;

    @Column(name = "ACTVTN_YN")
    @Comment("활성여부")
    private String actvtnYn = "Y";

    @Column(name = "CREATR_SN", updatable=false, nullable = false)
    @Comment("생성자일련번호")
    private long creatrSn = 1;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Column(name = "FRST_CRT_DT", updatable = false)
    @Comment("최초생성일시")
    private LocalDateTime frstCrtDt = LocalDateTime.now();
}
