package com.knowra.cmm.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙여 <b>현재 로그인한 사용자의 userSn</b>을 주입받는다.
 *
 * <p>⚠️ 이게 있기 전엔 컨트롤러가 {@code Authorization} 헤더 문자열을 서비스로 넘기고,
 * 서비스 33곳이 각자 {@code jwtProvider.extractUserSn(token.replace("Bearer ", ""))}
 * 로 다시 파싱했다. Spring Security 필터가 토큰을 검증해 {@code SecurityContext}에
 * 넣어 두는데 <b>아무도 읽지 않았다.</b> 인증은 "서비스가 기억해서 하면 되고, 잊으면
 * 안 되는" 구조였다.
 *
 * <p>이제 토큰 파싱은 {@link com.knowra.cmm.jwt.JwtAuthenticationFilter} 한 곳에서만
 * 일어나고, 컨트롤러는 이 애너테이션으로 결과만 받는다. 공개 엔드포인트(비로그인 열람
 * 가능)에서는 {@code null}이 들어온다 — 그래서 타입은 {@code Long}이어야 한다.
 * 로그인 필수 엔드포인트는 {@code SecurityConfig}가 먼저 401로 막으므로 null이 올 수 없다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
