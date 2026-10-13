package com.knowra.session;

/**
 * 세션이 끊겼다. {@code sid == null} 이면 그 사람의 모든 세션(계정 정지 등).
 *
 * <p>토큰 검사는 「새 요청」에서만 일어난다 — 이미 열린 웹소켓은 이 이벤트를 받아 직접 닫아야 한다.
 * 예전엔 다른 기기에서 세션을 끊어도 그 기기의 열린 소켓으로 알림이 계속 갔다.
 */
public record SessionRevokedEvent(long userSn, String sid) {}
