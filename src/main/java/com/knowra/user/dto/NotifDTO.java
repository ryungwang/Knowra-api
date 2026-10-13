package com.knowra.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knowra.user.entity.TblUserNotif;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
public class NotifDTO {

    private final Long    notifSn;
    private final String  notifTyp;
    private final String  message;
    private final String  senderNickName;
    private final String  senderPfpUrl;
    private final Long    targetSn;
    private final String  targetKind;
    private final String  createdAt;

    @JsonProperty("isRead")
    private final boolean isRead;

    /** 눌렀을 때 갈 곳 — 글·팔로워 프로필·커뮤니티. 없으면 null(그냥 읽음 처리만). */
    private final String link;

    public NotifDTO(TblUserNotif entity, String senderNickName, String senderPfpUrl) {
        this(entity, senderNickName, senderPfpUrl, null);
    }

    /** commNm: COMM 알림의 커뮤니티 주소 이름(호출부가 미리 찾아 준다). 예전엔 커뮤니티 알림을 눌러도 아무 데도 안 갔다. */
    public NotifDTO(TblUserNotif entity, String senderNickName, String senderPfpUrl, String commNm) {
        this.notifSn        = entity.getNotifSn();
        this.notifTyp       = entity.getNotifTyp();
        this.message        = entity.getMessage();
        this.senderNickName = senderNickName;
        this.senderPfpUrl   = senderPfpUrl;
        this.targetSn       = entity.getTargetSn();
        this.targetKind     = entity.getTargetKind();
        this.createdAt      = entity.getFrstCrtDt()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        this.isRead         = "Y".equals(entity.getIsRead());
        if ("POST".equals(entity.getTargetKind()) && entity.getTargetSn() != null) this.link = "/posts/" + entity.getTargetSn();
        else if ("FOLLOW".equals(entity.getNotifTyp()) && senderNickName != null) this.link = "/user/" + senderNickName;
        else if ("COMM".equals(entity.getTargetKind()) && commNm != null) this.link = "/community/" + commNm;
        else if ("BADGE".equals(entity.getTargetKind())) this.link = "/me/badges";   // 내 프로필 뱃지 칸(프론트가 내 닉네임으로 바꾼다)
        else this.link = null;
    }
}
