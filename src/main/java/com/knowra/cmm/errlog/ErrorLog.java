package com.knowra.cmm.errlog;

import org.slf4j.LoggerFactory;

/**
 * {@code e.printStackTrace()} 대신 쓴다. 표준 오류로만 찍히던 예외를 <b>ERROR 로그</b>로 남겨
 * {@link ErrorCollector} 가 모으게 한다(로거 이름은 부른 클래스).
 */
public final class ErrorLog {
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private ErrorLog() { }

    public static void report(Throwable e) {
        if (e instanceof NotFoundException) {   // 정상 결과 — 기록만 가볍게
            LoggerFactory.getLogger(WALKER.getCallerClass()).debug("not found: {}", e.getMessage());
            return;
        }
        LoggerFactory.getLogger(WALKER.getCallerClass()).error(String.valueOf(e), e);
    }
}
