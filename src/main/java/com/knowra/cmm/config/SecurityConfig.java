package com.knowra.cmm.config;

import com.knowra.cmm.jwt.JwtAuthenticationFilter;
import com.knowra.cmm.security.JsonAuthErrorHandler;
import lombok.RequiredArgsConstructor;
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
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jsonAuthErrorHandler)
                        .accessDeniedHandler(jsonAuthErrorHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 인증 자체. 로그아웃도 공개다 — 액세스 토큰이 이미 만료된 유저도
                        // 로그아웃(쿠키 삭제)은 할 수 있어야 한다. 서비스가 있는 토큰만 폐기한다.
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login", "/api/auth/snsLogin", "/api/auth/join",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()

                        // 비로그인 열람 — 피드·글·댓글
                        .requestMatchers(HttpMethod.GET, "/api/feed/**", "/api/post/getPost", "/api/search").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/post/getPostCmtList", "/api/post/viewPost", "/api/post/getCommPostList").permitAll()

                        // 비로그인 열람 — 커뮤니티
                        .requestMatchers(HttpMethod.GET,
                                "/api/community/getCommunity", "/api/community/getCommunityList",
                                "/api/community/explore", "/api/community/explore/filter").permitAll()

                        // 비로그인 열람 — 공개 프로필
                        .requestMatchers(HttpMethod.POST,
                                "/api/user/getUserProfile", "/api/user/getUserPostList",
                                "/api/user/getUserFollowerList", "/api/user/getUserFollowingList").permitAll()

                        // 정적·기타
                        .requestMatchers(HttpMethod.GET, "/api/common/getCategoryList", "/upload/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // 관리자 — 역할까지 본다(토큰 role 클레임 → ROLE_ADMIN)
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
