package com.knowra.cmm.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 리프레시 토큰을 <b>HttpOnly 쿠키</b>로 주고받는다.
 *
 * <p>⚠️ 예전엔 리프레시 토큰을 응답 본문으로 내려 프론트가 {@code localStorage}에 뒀다.
 * XSS 한 번이면 7일짜리 세션이 통째로 새는 구조다. HttpOnly 쿠키는 스크립트가 못 읽는다.
 *
 * <p>경로를 {@code /api/auth}로 묶어 일반 API 호출엔 아예 실리지 않게 한다 — 쿠키가 가는
 * 곳은 로그인·갱신·로그아웃뿐이다. {@code SameSite=Lax}는 프론트(5111)와 API(8081)가
 * 같은 사이트(같은 eTLD+1)일 때 fetch 에도 실린다. 운영에서 프론트와 API 도메인의
 * eTLD+1이 다르면 {@code None} + Secure 로 바꿔야 한다.
 *
 * <p>{@code Secure}는 설정으로 켠다 — 로컬 http 에서 켜면 브라우저가 쿠키를 버린다.
 */
@Component
public class RefreshCookie {

    public static final String PATH = "/api/auth";

    private final String name;
    private final boolean secure;
    private final long maxAgeSeconds;

    public RefreshCookie(@Value("${knowra.auth.refresh-cookie-name:knowra_rt}") String name,
                         @Value("${knowra.auth.cookie-secure:false}") boolean secure,
                         @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpirationMs) {
        this.name = name;
        this.secure = secure;
        this.maxAgeSeconds = refreshTokenExpirationMs / 1000;
    }

    /** 발급·갱신 때 내려보낼 Set-Cookie 값. */
    public String issue(String refreshToken) {
        return ResponseCookie.from(name, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build().toString();
    }

    /** 로그아웃 때 내려보낼 Set-Cookie 값(즉시 만료). */
    public String clear() {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(0)
                .build().toString();
    }

    /** 요청에 실린 리프레시 토큰. 없으면 null. */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
