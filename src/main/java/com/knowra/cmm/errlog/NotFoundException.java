package com.knowra.cmm.errlog;

/**
 * "없다"는 정상 결과(지운 글, 바뀐 닉네임, 잘못 친 주소)일 때 쓴다. {@link ErrorLog#report} 가
 * 오류 모음에 올리지 않는다 — 안 그러면 주소를 잘못 친 방문만으로도 「열린 오류」가 쌓인다.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message, null, false, false); }
}
