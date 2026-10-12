package com.knowra.cmm.jwt;

import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 발급·검증.
 *
 * <p>토큰에는 <b>신원과 역할만</b> 담는다 — userSn·loginId·nickName·role·typ.
 * 예전엔 프로필 이미지 URL 까지 넣었는데, 그 값에 {@code http://127.0.0.1:8080} 이
 * 하드코딩돼 있어 운영 토큰이 로컬 주소를 들고 다녔다. 토큰은 발급 시점에 박제되므로
 * 바뀌는 데이터(프로필 사진·닉네임)는 원래 들어갈 자리가 아니다. 프로필은 API 로 조회한다.
 * nickName 은 프론트가 로그인 직후 표시에 쓰고 있어 당장은 남긴다.
 *
 * <p>{@code typ} 클레임({@code access}/{@code refresh})으로 두 토큰을 구분한다.
 * 형식이 같아서 구분하지 않으면 리프레시 토큰을 Bearer 로 써도 통과한다.
 */
@Component
public class JwtProvider {

    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final TblUserRepository tblUserRepository;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration:3600000}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration,
            TblUserRepository tblUserRepository) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.tblUserRepository = tblUserRepository;
    }

    public String generateAccessToken(long userSn, String loginId) {
        return buildToken(userSn, loginId, accessTokenExpiration, TYP_ACCESS);
    }

    public String generateRefreshToken(long userSn, String loginId) {
        return buildToken(userSn, loginId, refreshTokenExpiration, TYP_REFRESH);
    }

    private String buildToken(long userSn, String loginId, long expiration, String typ) {
        Date now = new Date();
        TblUser tblUser = tblUserRepository.findByUserSn(userSn);

        return Jwts.builder()
                // ⚠️ jti 가 없으면 같은 초에 발급된 토큰이 글자까지 똑같다(iat/exp 가 초 단위).
                // 리프레시 회전은 "옛 토큰을 폐기하고 새 토큰을 준다"인데, 둘이 같으면 방금 준
                // 토큰을 폐기하는 꼴이 된다 — 가입 직후 갱신에서 실제로 그렇게 됐다.
                .id(UUID.randomUUID().toString())
                .claim("loginId", loginId)
                .claim("nickName", tblUser != null ? tblUser.getNickName() : null)
                .claim("userSn", userSn)
                // TODO 역할을 DB 컬럼으로 — 지금은 1번 사용자만 관리자라는 암묵 규칙이다.
                .claim("role", userSn == 1 ? "ADMIN" : "USER")
                .claim("typ", typ)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    public long extractUserSn(String token) {
        return parseClaims(token).get("userSn", Long.class);
    }

    public String extractLoginId(String token) {
        return parseClaims(token).get("loginId", String.class);
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean isAccessToken(String token) {
        return TYP_ACCESS.equals(parseClaims(token).get("typ", String.class));
    }

    public boolean isRefreshToken(String token) {
        return TYP_REFRESH.equals(parseClaims(token).get("typ", String.class));
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getRemainingTtlSeconds(String token) {
        Date expiration = parseClaims(token).getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(remaining / 1000, 0);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
