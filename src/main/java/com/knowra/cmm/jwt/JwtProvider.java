package com.knowra.cmm.jwt;

import com.knowra.common.entity.QTblComFile;
import com.knowra.common.entity.TblComFile;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    @PersistenceContext
    private EntityManager em;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration:3600000}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(long userSn, String loginId) {
        return buildToken(userSn, loginId, accessTokenExpiration);
    }

    public String generateRefreshToken(long userSn, String loginId) {
        return buildToken(userSn, loginId, refreshTokenExpiration);
    }

    private String buildToken(long userSn, String loginId, long expiration) {
        Date now = new Date();

        QTblComFile qProFile = QTblComFile.tblComFile;
        TblComFile proFile = new JPAQueryFactory(em)
            .selectFrom(qProFile)
            .where(
                qProFile.psnTblSn.eq(
                    Expressions.stringTemplate("CONCAT('user_', {0})", userSn)
                ))
            .fetchOne();

        JwtBuilder buildJwt = Jwts.builder()
                .claim("loginId", loginId)
                .claim("userSn", userSn)
                .claim("role", userSn == 1 ? "ADMIN" : "USER")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(secretKey);
        if(proFile != null) {
            buildJwt.claim("proFile", "http://127.0.0.1:8080" + proFile.getAtchFilePathNm() + "/" + proFile.getStrgFileNm() + "." + proFile.getAtchFileExtnNm());
        }

        return buildJwt.compact();
    }

    public long extractUserSn(String token) {
        return parseClaims(token).get("userSn", Long.class);
    }

    public String extractLoginId(String token) {
        return parseClaims(token).get("loginId", String.class);
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
