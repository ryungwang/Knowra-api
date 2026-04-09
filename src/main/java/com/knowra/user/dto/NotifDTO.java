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

    public NotifDTO(TblUserNotif entity, String senderNickName, String senderPfpUrl) {
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
    }
}
