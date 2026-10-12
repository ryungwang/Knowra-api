package com.knowra.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/** 1:1 대화방. {@code dmKey} = "작은SN:큰SN" 으로 같은 두 사람 사이에 방이 하나만 생긴다. */
@Entity
@Table(name = "TBL_CHAT_ROOM", schema = "KNOWRA_USER")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TblChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ROOM_SN")
    private Long roomSn;

    @Builder.Default
    @Column(name = "ROOM_TYP", length = 10, nullable = false)
    @Comment("DM")
    private String roomTyp = "DM";

    @Column(name = "DM_KEY", length = 50)
    @Comment("min(userSn):max(userSn)")
    private String dmKey;

    @Column(name = "LAST_MSG_SN")
    private Long lastMsgSn;

    @Column(name = "LAST_MSG_AT")
    private LocalDateTime lastMsgAt;

    @Builder.Default
    @Column(name = "FRST_CRT_DT", nullable = false, updatable = false)
    private LocalDateTime frstCrtDt = LocalDateTime.now();

    public static String dmKeyOf(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }
}
