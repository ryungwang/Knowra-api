# Knowra 로그인 구현 전체 단계 가이드

> 스택: Spring Boot · Spring Security · JWT · BCrypt · MariaDB
> 최종 수정: 2026-03-26

---

## 진행 현황 범례
- `[ ]` 미완료
- `[x]` 완료
- `[-]` 진행 중

---

## Step 1. 의존성 추가 (`pom.xml`)

- [ ] jjwt-api 추가
- [ ] jjwt-impl 추가
- [ ] jjwt-jackson 추가
- [ ] spring-security-crypto (BCrypt) → spring-boot-starter-security에 포함됨 확인

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
<groupId>io.jsonwebtoken</groupId>
<artifactId>jjwt-impl</artifactId>
<version>0.11.5</version>
<scope>runtime</scope>
</dependency>
<dependency>
<groupId>io.jsonwebtoken</groupId>
<artifactId>jjwt-jackson</artifactId>
<version>0.11.5</version>
<scope>runtime</scope>
</dependency>
```

---

## Step 2. `application.properties` 설정

- [ ] jwt.secret 추가 (32자 이상)
- [ ] jwt.expiration 추가

```properties
# JWT
jwt.secret=knowra-secret-key-must-be-at-least-32-characters-long
jwt.access-token-expiration=3600000
jwt.refresh-token-expiration=604800000
```

---

## Step 3. DB 테스트 데이터 세팅

- [ ] `/api/auth/encode` 임시 API로 BCrypt 값 추출 (1111 → BCrypt)
- [ ] 아래 쿼리로 테스트 유저 INSERT or UPDATE

```sql
-- 테스트 유저 삽입 (BCrypt 값은 /encode API에서 뽑은 값)
INSERT INTO tbl_user (email, password)
VALUES ('deerrk', '뽑은_BCrypt값');

-- 이미 있으면
UPDATE tbl_user
SET password = '뽑은_BCrypt값'
WHERE email = 'deerrk';
```

- [ ] `/api/auth/encode` 임시 API 삭제

---

## Step 4. `SecurityConfig` 설정

- [ ] `PasswordEncoder` 빈 등록
- [ ] Security FilterChain 설정 (로그인 API는 인증 없이 허용)

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()  // 로그인은 허용
                        .anyRequest().authenticated()                 // 나머지는 인증 필요
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

## Step 5. `JwtUtil` 작성

- [ ] 토큰 생성 메서드 (`generateToken`)
- [ ] 토큰 파싱 메서드 (`getUserSn`)

```java
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;  // 1시간

    public String generateToken(Integer userSn) {
        return Jwts.builder()
                .claim("userSn", userSn)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes()))
                .compact();
    }

    public Integer getUserSn(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("userSn", Integer.class);
    }
}
```

---

## Step 6. `JwtFilter` 작성

- [ ] `OncePerRequestFilter` 상속
- [ ] Authorization 헤더에서 토큰 추출
- [ ] SecurityContext에 USER_SN 저장

```java
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Integer userSn = jwtUtil.getUserSn(token);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userSn, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
```

---

## Step 7. DTO 작성

- [ ] `LoginRequest` 작성
- [ ] `LoginResponse` 작성

```java
// 요청
@Getter
public class LoginRequest {
    private String loginId;   // 이메일 or 아이디
    private String password;
}

// 응답
@Getter
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
}
```

---

## Step 8. `TblUserRepository` 작성

- [ ] `findByEmail` 메서드 추가

```java
public interface TblUserRepository extends JpaRepository<TblUser, Integer> {
    Optional<TblUser> findByEmail(String email);
}
```

---

## Step 9. `AuthService` 작성

- [ ] email로 유저 조회
- [ ] BCrypt 비밀번호 검증
- [ ] JWT 토큰 발급 및 반환

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final TblUserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        TblUser user = userRepository.findByEmail(request.getLoginId())
                .orElseThrow(() -> new RuntimeException("존재하지 않는 계정입니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }

        String token = jwtUtil.generateToken(user.getUserSn());
        return new LoginResponse(token);
    }
}
```

---

## Step 10. `AuthController` 작성

- [ ] `POST /api/auth/login` API 작성

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
```

---

## Step 11. Postman 테스트

- [ ] 서버 실행 확인
- [ ] 로그인 API 호출

```
POST /api/auth/login
Content-Type: application/json

{
    "loginId": "deerrk",
    "password": "1111"
}
```

- [ ] 응답에서 `accessToken` 확인
- [ ] 다른 API 호출 시 헤더에 토큰 추가

```
Authorization: Bearer {accessToken}
```

- [ ] 커뮤니티 생성 API에서 USER_SN 정상 추출되는지 확인

---

## Step 12. 커뮤니티 API에 JWT 연동

- [ ] `CommunityController`에서 `@AuthenticationPrincipal Integer userSn` 적용

```java
@PostMapping
public ResponseEntity<?> createCommunity(
        @RequestBody CommunityCreateRequest request,
        @AuthenticationPrincipal Integer userSn) {

    communityService.create(request, userSn);
    return ResponseEntity.ok().build();
}
```

---

## 전체 순서 요약

```
Step 1  → pom.xml 의존성 추가
Step 2  → application.yml 설정
Step 3  → DB 테스트 데이터 세팅 (BCrypt)
Step 4  → SecurityConfig 설정
Step 5  → JwtUtil 작성
Step 6  → JwtFilter 작성
Step 7  → DTO 작성
Step 8  → TblUserRepository 작성
Step 9  → AuthService 작성
Step 10 → AuthController 작성
Step 11 → Postman 테스트
Step 12 → 커뮤니티 API에 JWT 연동
```

---

> ⚠️ `/api/auth/encode` 임시 API는 Step 3 완료 후 반드시 삭제
> ✅ Step 12 완료되면 커뮤니티 작업 이어서 진행