package com.knowra.cmm.config;

import com.knowra.cmm.jwt.JwtAuthenticationFilter;
import com.knowra.cmm.security.JsonAuthErrorHandler;
import lombok.RequiredArgsConstructor;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 인증 경계.
 *
 * <p>⚠️ <b>기본은 인증 필수다.</b> 예전엔 {@code /api/**} 전체가 {@code permitAll} 이라
 * "인증이 필요한지"를 서비스 33곳이 각자 기억해야 했다 — 잊으면 익명 쓰기가 됐고,
 * 이미지 업로드({@code /api/common/uploadImage})는 아무 검사도 없었다.
 * 지금은 <b>비로그인 열람이 필요한 경로만</b> 아래에 명시한다. 새 엔드포인트는
 * 아무것도 안 하면 인증 필수가 된다 — 빠뜨렸을 때 가는 쪽이 안전한 쪽이어야 한다.
 *
 * <p>공개 경로를 고를 때의 기준: <b>로그인 없이 봐야 유입이 생기는 것</b>(피드·글·커뮤니티·
 * 프로필 열람)만. 그 서비스들은 {@code @CurrentUser Long me}가 null 이어도 동작하게
 * 돼 있다(내 좋아요 여부 같은 건 비어서 온다).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final JsonAuthErrorHandler jsonAuthErrorHandler;

    /** 쉼표 구분. 운영은 환경변수로 받는다 — {@code *} 는 credentials 와 같이 쓸 수 없다. */
    @Value("${knowra.cors.allowed-origins:http://localhost:5111,http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // 토큰은 헤더로, 리프레시 쿠키는 /api/auth 로 묶여 있다
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // ⚠️ STATELESS 만으로는 세션이 안 생기는 게 아니다. 401 이 나면 ExceptionTranslationFilter 가
                // "로그인 뒤 돌아갈 요청"을 HttpSession 에 저장하려고 세션을 만든다(SPRING_SECURITY_SAVED_REQUEST).
                // 그 세션 쿠키가 한 번 생기면 이후 모든 요청이 세션을 물고 다녔고, spring-session(redis) 이
                // 동시 요청마다 세션 ID 를 바꾸다 RENAME 충돌("ERR no such key") → /error → 401 로 튀었다
                // (2026-10-13: 멀쩡한 액세스 토큰이 요청 3개 중 2개만 401). JWT 앱엔 돌아갈 요청 저장이
                // 필요 없다 — 세션을 만들 이유를 아예 없앤다. spring-session 의존성도 같은 이유로 걷어냈다.
                .requestCache(rc -> rc.requestCache(new NullRequestCache()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jsonAuthErrorHandler)
                        .accessDeniedHandler(jsonAuthErrorHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 오류 응답 경로. 막혀 있으면 잘못된 JSON(400)·서버 오류(500)까지 전부 "로그인이 필요합니다"(401)로
                        // 둔갑해 원인을 가렸다 — 프론트는 401 에 토큰을 갱신하고 재시도까지 한다.
                        .requestMatchers("/error").permitAll()

                        // 인증 자체. 로그아웃도 공개다 — 액세스 토큰이 이미 만료된 유저도
                        // 로그아웃(쿠키 삭제)은 할 수 있어야 한다. 서비스가 있는 토큰만 폐기한다.
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login", "/api/auth/login/2fa", "/api/auth/snsLogin", "/api/auth/join",
                                "/api/auth/refresh", "/api/auth/logout",
                                // 가입 전 인증 코드(메일·문자) · 비밀번호 재설정 — 로그인 전에 쓰는 것들
                                "/api/auth/verify/send", "/api/auth/verify/check",
                                "/api/auth/password/forgot", "/api/auth/password/reset",
                                "/api/digest/unsubscribe", "/api/digest/oneclick",
                                "/api/mail/sns",   // SES 반송·스팸 신고 알림 — SNS 서명으로 확인한다(MailFeedbackController)
                                "/api/err/report").permitAll()

                        // 비로그인 열람 — 피드·글·댓글
                        .requestMatchers(HttpMethod.GET, "/api/notice/current", "/api/sitemap.xml", "/api/auth/sns/providers", "/api/push/publicKey", "/api/link/preview", "/api/link/image", "/api/badge/list").permitAll()
                        // 공유 링크 미리보기 — 봇은 로그인하지 않는다. 비로그인 기준 공개 정보만 낸다(ShareController).
                        .requestMatchers(HttpMethod.GET, "/s/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/feed/**", "/api/post/getPost", "/api/post/getPostHistory", "/api/post/getCmtHistory", "/api/series/get", "/api/series/byUser", "/api/search", "/api/search/suggest", "/api/tag/info", "/api/tag/posts", "/api/legal/info", "/api/appeal/suspended").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/post/getPostCmtList", "/api/post/viewPost", "/api/post/getCommPostList",
                                "/api/translate/post", "/api/translate/comments", "/api/translate/titles", "/api/translate/community", "/api/translate/events", "/api/translate/communityCards", "/api/translate/wiki",   // 비로그인은 이미 해 둔 번역만(TranslationService.call)
                                "/api/appeal/suspended",
                                "/api/copyright/notice").permitAll()   // 권리자는 회원이 아닐 수 있다   // 정지돼 로그인할 수 없는 사람의 이의 — 로그인 때 받은 확인표로만

                        // 비로그인 열람 — 커뮤니티
                        .requestMatchers(HttpMethod.GET,
                                "/api/community/getCommunity", "/api/community/getCommunityList", "/api/community/flairs", "/api/community/joinQuestions",
                                "/api/community/explore", "/api/community/explore/filter",
                                "/api/community/wiki/overview", "/api/community/wiki/page",
                                "/api/community/event/list", "/api/community/event/detail",
                                "/api/community/invite/*").permitAll()   // 초대 받는 화면은 로그인 전에도 보인다

                        // 비로그인 열람 — 공개 프로필
                        .requestMatchers(HttpMethod.POST,
                                "/api/user/getUserProfile", "/api/user/getUserPostList",
                                "/api/user/getUserFollowerList", "/api/user/getUserFollowingList").permitAll()

                        // 정적·기타
                        .requestMatchers(HttpMethod.GET, "/api/common/getCategoryList", "/upload/**", "/api/files/**").permitAll()   // /api/files = 로컬·dev 저장소(공개 파일만)
                        .requestMatchers("/ws/**").permitAll()

                        // 검수자(MOD)도 쓰는 것 — 신고·이의 제기·사용자 정지·기록 보기. 관리자는 ROLE_MOD 도 갖는다
                        .requestMatchers(HttpMethod.POST, Stream.of("stats", "reportList", "setMyModLangs", "handleReport", "deletePost", "deleteCmt", "hideMsg",
                                "userList", "setUserSuspend", "appealList", "appealDecide", "auditLog")
                                .map(p -> "/api/admin/console/" + p).toArray(String[]::new)).hasRole("MOD")
                        // 그 밖의 관리 — 관리자만(토큰 role 클레임 → ROLE_ADMIN). 역할 지정·공지·금칙어·오류·저작권·콘텐츠 생성
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // 리프레시 쿠키 때문에 필요하다 — 그래서 origin 이 * 일 수 없다

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
