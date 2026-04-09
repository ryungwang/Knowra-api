package com.knowra.cmm.event;

/**
 * 알림 생성 트리거 이벤트.
 * receiverSn == senderSn 이면 NotificationEventListener에서 자동 무시.
 */
public record NotifTriggerEvent(
        long   receiverSn,
        Long   senderSn,
        String notifTyp,
        Long   targetSn,
        String targetKind
) {}
