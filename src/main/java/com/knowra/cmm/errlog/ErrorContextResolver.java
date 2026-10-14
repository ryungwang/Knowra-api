package com.knowra.cmm.errlog;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * 처리 안 된 요청 예외의 "어느 요청·누구"를 잠깐 맡아 둔다. 그런 예외는 톰캣이 <b>요청 문맥이
 * 다 정리된 뒤</b> ERROR 로 찍어서, 수집기가 그 순간엔 경로도 사용자도 알 수 없다.
 * 예외를 처리하지는 않는다(null 반환) — 응답은 원래대로 나간다.
 */
@Component
public class ErrorContextResolver implements HandlerExceptionResolver, Ordered {

    record Ctx(String path, Long userSn, long at) {}

    static final ThreadLocal<Ctx> LAST = new ThreadLocal<>();

    @Override
    public ModelAndView resolveException(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long user = auth != null && auth.getPrincipal() instanceof Long u ? u : null;
        LAST.set(new Ctx(req.getMethod() + " " + req.getRequestURI(), user, System.currentTimeMillis()));
        return null;
    }

    /** 같은 스레드에서 방금(2초 안) 맡긴 것만 꺼낸다 — 스레드가 재사용돼도 엉뚱한 요청이 붙지 않게. */
    static Ctx take() {
        Ctx c = LAST.get();
        LAST.remove();
        return c != null && System.currentTimeMillis() - c.at() < 2000 ? c : null;
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
