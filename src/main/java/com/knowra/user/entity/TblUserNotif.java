package com.knowra.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_USER_NOTIF", catalog = "KNOWRA_USER")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TblUserNotif {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTIF_SN")
    @Comment("알림일련번호")
    private Long notifSn;

    @Column(name = "USER_SN", nullable = false)
    @Comment("수신자일련번호")
    private Long userSn;

    @Column(name = "SENDER_SN")
    @Comment("발신자일련번호 (SYSTEM 공지는 NULL)")
    private Long senderSn;

    @Column(name = "NOTIF_TYP", length = 20, nullable = false)
    @Comment("알림유형 COMMENT|LIKE|FOLLOW|SYSTEM")
    private String notifTyp;

    @Column(name = "MESSAGE", length = 255, nullable = false)
    @Comment("알림메시지")
    private String message;

    @Column(name = "TARGET_SN")
    @Comment("대상게시글/유저 SN")
    private Long targetSn;

    @Column(name = "TARGET_KIND", length = 20)
    @Comment("대상종류 POST|COMM_POST")
    private String targetKind;

    @Builder.Default
    @Column(name = "IS_READ", columnDefinition = "CHAR(1) DEFAULT 'N'", nullable = false)
    @Comment("읽음여부")
    private String isRead = "N";

    @Builder.Default
    @Column(name = "IS_DISPLAY", columnDefinition = "CHAR(1) DEFAULT 'Y'", nullable = false)
    @Comment("표시여부 (N = X 버튼으로 숨김)")
    private String isDisplay = "Y";

    @Builder.Default
    @Column(name = "FRST_CRT_DT", columnDefinition = "DATETIME DEFAULT NOW()", updatable = false)
    @Comment("생성일시")
    private LocalDateTime frstCrtDt = LocalDateTime.now();
}
