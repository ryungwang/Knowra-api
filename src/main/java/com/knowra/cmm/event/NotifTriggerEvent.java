package com.knowra.cmm.event;

/**
 * 알림 생성 트리거 이벤트.
 * receiverSn == senderSn 이면 NotificationEventListener에서 자동 무시.
 *
 * <p>notifTyp: LIKE · COMMENT · REPLY · FOLLOW · COMM_POST · COMM_NOTICE · SYSTEM.
 * {@code extra} 는 메시지 조립 재료(커뮤니티 표시 이름 등)이며 없으면 null.
 */
public record NotifTriggerEvent(
        long   receiverSn,
        Long   senderSn,
        String notifTyp,
        Long   targetSn,
        String targetKind,
        String extra
) {
    public NotifTriggerEvent(long receiverSn, Long senderSn, String notifTyp, Long targetSn, String targetKind) {
        this(receiverSn, senderSn, notifTyp, targetSn, targetKind, null);
    }
}
