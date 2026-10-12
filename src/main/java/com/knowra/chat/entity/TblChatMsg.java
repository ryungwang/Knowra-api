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
}
