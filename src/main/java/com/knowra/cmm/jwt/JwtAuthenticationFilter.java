package com.knowra.cmm.jwt;

import com.knowra.cmm.security.JsonAuthErrorHandler;
import com.knowra.cmm.service.RedisApiService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bearer 토큰을 검증해 {@code SecurityContext}에 principal({@code Long userSn})을 넣는다.
 * <b>토큰 파싱은 앱 전체에서 여기 한 곳</b>이다 — 컨트롤러는 {@code @CurrentUser}로 결과만 받는다.
 *
 * <p>토큰이 <b>없으면</b> 익명으로 통과시킨다(공개 열람 경로가 있다 — 막을지는
 * {@code SecurityConfig}가 정한다). 토큰이 <b>있는데 틀리면</b> 여기서 바로 401 이다.
 * 예전엔 틀린 토큰도 익명으로 흘려보내서, 만료된 토큰으로 글을 쓰려던 유저가
 * "로그인이 필요합니다"가 아니라 서비스 내부 NPE 로 "조회 오류"를 받았다.
 *
 * <p>리프레시 토큰을 Bearer 로 들이미는 것도 막는다({@code typ} 클레임). 두 토큰은 서명·
 * 형식이 같아서, 구분하지 않으면 7일짜리 리프레시 토큰이 액세스 토큰 노릇을 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final RedisApiService redisApiService;
    private final JsonAuthErrorHandler jsonAuthErrorHandler;
    private final com.knowra.session.SessionService sessionService;

    public static final int REDIS_DB = 15;
    /** 이 요청의 세션 id(요청 속성 이름) */
    public static final String SID_ATTR = "knowra.sid";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String sid = jwtProvider.isValid(token) ? jwtProvider.extractSid(token) : null;
        if (!jwtProvider.isValid(token)
                || !jwtProvider.isAccessToken(token)
                || sid == null   // 세션 없는 토큰은 끊을 수가 없다 — 프로필 수정이 그런 토큰을 내줘 「다른 기기 로그아웃」·정지로도 못 막았다
                || redisApiService.isBlocklisted(REDIS_DB, token)
                || sessionService.isRevoked(sid)) {   // 다른 기기에서 끊은 세션
            jsonAuthErrorHandler.commence(request, response,
                    new BadCredentialsException("invalid or revoked access token"));
            return;
        }

        long userSn = jwtProvider.extractUserSn(token);
        List<GrantedAuthority> authorities = "ADMIN".equals(jwtProvider.extractRole(token))
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userSn, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        request.setAttribute(SID_ATTR, sid);   // 토큰을 새로 내줄 때(프로필 수정 등) 같은 세션에 싣는다
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
