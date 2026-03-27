package com.knowra.cmm.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration:3600000}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(long userSn, String email) {
        return buildToken(userSn, email, accessTokenExpiration);
    }

    public String generateRefreshToken(long userSn, String email) {
        return buildToken(userSn, email, refreshTokenExpiration);
    }

    private String buildToken(long userSn, String email, long expiration) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userSn))  // subject에 userSn
                .claim("email", email)             // claim에 email 추가
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey)
                .compact();
    }

    public long extractUserSn(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
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
