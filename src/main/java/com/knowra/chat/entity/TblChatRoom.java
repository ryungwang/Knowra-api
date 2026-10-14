package com.knowra.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/** 대화방 — DM(1:1, {@code dmKey} = "작은SN:큰SN" 으로 두 사람 사이에 하나) 또는 GROUP(이름 있는 여럿, V32). */
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

    /** GROUP 방 이름 */
    @Column(name = "NAME", length = 50)
    private String name;

    /** GROUP 방을 만든 사람 — 초대는 이 사람만. 나가면 남은 사람 중 가장 먼저 들어온 사람에게 넘긴다 */
    @Column(name = "CREATED_BY")
    private Long createdBy;

    /** 모임 대화방이면 그 모임 — 모임당 하나 */
    @Column(name = "EVENT_SN")
    private Long eventSn;

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
