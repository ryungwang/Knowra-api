package com.knowra.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/** 대화방 참여자. 읽음은 메시지마다가 아니라 "여기까지 읽음"(lastReadMsgSn) 하나로 센다. */
@Entity
@Table(name = "TBL_CHAT_ROOM_MBR", schema = "KNOWRA_USER")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblChatRoomMbr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ROOM_MBR_SN")
    private Long roomMbrSn;

    @Column(name = "ROOM_SN", nullable = false)
    private long roomSn;

    @Column(name = "USER_SN", nullable = false)
    private long userSn;

    @Builder.Default
    @Column(name = "LAST_READ_MSG_SN", nullable = false)
    @Comment("이 번호까지 읽었다")
    private long lastReadMsgSn = 0;

    @Builder.Default
    @Column(name = "FRST_CRT_DT", nullable = false, updatable = false)
    private LocalDateTime frstCrtDt = LocalDateTime.now();
}
