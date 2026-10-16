package com.knowra.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_CHAT_MSG", schema = "KNOWRA_USER")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblChatMsg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MSG_SN")
    private Long msgSn;

    @Column(name = "ROOM_SN", nullable = false)
    private long roomSn;

    @Column(name = "USER_SN", nullable = false)
    private long userSn;

    @Column(name = "MSG_CNTNT", columnDefinition = "TEXT", nullable = false)
    private String msgCntnt;

    @Builder.Default
    @Column(name = "FRST_CRT_DT", nullable = false, updatable = false)
    private LocalDateTime frstCrtDt = LocalDateTime.now();

    /** 사진 한 장 — 업로드 경로(/upload/editor/...)만. */
    /** 답한 메시지(같은 방). V57 */
    @Column(name = "REPLY_TO_MSG_SN")
    private Long replyToMsgSn;

    @Column(name = "IMG_URL", length = 300)
    private String imgUrl;

    /** 운영 관리자가 가린 때 — 신고 처리. 내용은 남기고(재신고 때 다시 본다) 화면에만 안 보인다 */
    @Column(name = "HIDDEN_AT")
    private LocalDateTime hiddenAt;

    /** 보낸 사람이 지운 때(V37) — 화면엔 「삭제된 메시지」, 내용은 남긴다(신고 확인용) */
    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;
}
