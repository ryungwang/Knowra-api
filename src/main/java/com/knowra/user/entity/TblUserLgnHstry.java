package com.knowra.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_USER_LGN_HSTRY", catalog = "KNOWRA_USER")
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
    @Column(name = "LGN_DT", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", updatable = false)
    @Comment("로그인일시")
    private LocalDateTime lgnDt = LocalDateTime.now();

    @Column(name = "LGN_IP", nullable = false, updatable = false)
    @Comment("활성여부")
    private String lgnIp;

    @Column(name = "ACTVTN_YN", columnDefinition = "CHAR(1) DEFAULT 'Y'")
    @Comment("활성여부")
    private String actvtnYn = "Y";

    @Column(name = "CREATR_SN", columnDefinition = "INT(10)", updatable=false, nullable = false)
    @Comment("생성자일련번호")
    private long creatrSn;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Column(name = "FRST_CRT_DT", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", updatable = false)
    @Comment("최초생성일시")
    private LocalDateTime frstCrtDt = LocalDateTime.now();
}
