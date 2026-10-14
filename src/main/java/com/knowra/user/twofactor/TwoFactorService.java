package com.knowra.user.twofactor;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.knowra.cmm.jwt.JwtAuthenticationFilter;
import com.knowra.cmm.service.RedisApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2단계 인증(TOTP).
 *
 * <p>켜기: setup 이 비밀을 Redis 에 10분 맡기고 QR 을 준다 → enable 에서 앱 코드가 맞아야 DB 에 옮긴다
 * (QR 만 받고 앱에 안 넣은 채 켜지면 그 계정은 영영 못 들어온다). 켜면 복구 코드 8개를 한 번만 보여 준다.
 *
 * <p>로그인: 비밀번호가 맞으면 토큰 대신 5분짜리 확인표(ticket)를 준다 → 코드가 맞아야 토큰. 확인표마다 5번까지,
 * 같은 코드는 다시 못 쓴다(엿본 코드를 30초 안에 재사용하는 것 막기).
 */
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private static final int DB = JwtAuthenticationFilter.REDIS_DB;
    private static final int SETUP_TTL = 600, TICKET_TTL = 300, MAX_TRIES = 5, RECOVERY_COUNT = 8;
    private static final String ISSUER = "Knowra";
    private static final SecureRandom RNG = new SecureRandom();
    // 헷갈리는 글자(0/O, 1/I/L) 뺀 복구 코드 글자
    private static final String RC_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final JdbcTemplate jdbc;
    private final RedisApiService redis;

    public boolean isEnabled(long userSn) {
        Boolean on = jdbc.query("SELECT totp_enabled_at IS NOT NULL AND totp_secret IS NOT NULL FROM knowra_user.tbl_user WHERE user_sn = ?",
                rs -> rs.next() && rs.getBoolean(1), userSn);
        return Boolean.TRUE.equals(on);
    }

    public Map<String, Object> status(long me) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", isEnabled(me));
        m.put("recoveryLeft", jdbc.queryForObject("SELECT COUNT(*) FROM knowra_user.tbl_user_recovery_code WHERE user_sn = ? AND used_at IS NULL", Long.class, me));
        return m;
    }

    public Map<String, Object> setup(long me) {
        if (isEnabled(me)) throw new IllegalArgumentException("이미 켜져 있어요.");
        String account = jdbc.queryForObject("SELECT COALESCE(email, login_id) FROM knowra_user.tbl_user WHERE user_sn = ?", String.class, me);
        String secret = Totp.newSecret();
        redis.setRedis(DB, "2fa:setup:" + me, secret, SETUP_TTL);
        String uri = Totp.uri(ISSUER, account, secret);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("secret", secret);
        m.put("uri", uri);
        m.put("qr", qrDataUrl(uri));
        return m;
    }

    @Transactional
    public List<String> enable(long me, String code) {
        Object pending = redis.getRedis(DB, "2fa:setup:" + me);
        if (pending == null) throw new IllegalArgumentException("설정 시간이 지났어요. 처음부터 다시 해 주세요.");
        long c = Totp.verify(pending.toString(), code, Instant.now().getEpochSecond());
        if (c < 0) throw new IllegalArgumentException("코드가 맞지 않아요. 인증 앱의 지금 코드를 넣어 주세요.");
        markUsed(me, c);
        jdbc.update("UPDATE knowra_user.tbl_user SET totp_secret = ?, totp_enabled_at = ? WHERE user_sn = ?", pending.toString(), LocalDateTime.now(), me);
        redis.delRedis(DB, "2fa:setup:" + me);
        return newRecoveryCodes(me);
    }

    /** 끄기·복구 코드 다시 받기 — 지금 코드(또는 복구 코드)로 본인임을 다시 확인한다 */
    @Transactional
    public void disable(long me, String code) {
        requireCode(me, code);
        jdbc.update("UPDATE knowra_user.tbl_user SET totp_secret = NULL, totp_enabled_at = NULL WHERE user_sn = ?", me);
        jdbc.update("DELETE FROM knowra_user.tbl_user_recovery_code WHERE user_sn = ?", me);
    }

    @Transactional
    public List<String> regenerateRecovery(long me, String code) {
        requireCode(me, code);
        return newRecoveryCodes(me);
    }

    /** 비밀번호까지 맞은 로그인 — 토큰 대신 확인표 */
    public String issueTicket(long userSn) {
        byte[] b = new byte[24];
        RNG.nextBytes(b);
        String t = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        redis.setRedis(DB, "2fa:ticket:" + t, String.valueOf(userSn), TICKET_TTL);
        return t;
    }

    /** 확인표 + 코드 → 사용자. 틀리면 IllegalArgumentException(남은 횟수 안내), 다 쓰면 확인표를 없앤다 */
    @Transactional
    public long completeLogin(String ticket, String code) {
        String key = "2fa:ticket:" + (ticket == null ? "" : ticket);
        Object v = redis.getRedis(DB, key);
        if (v == null) throw new IllegalArgumentException("확인 시간이 지났어요. 처음부터 다시 로그인해 주세요.");
        long userSn = Long.parseLong(v.toString());
        long tries = redis.incr(DB, key + ":tries", TICKET_TTL);
        if (tries > MAX_TRIES) {
            redis.delRedis(DB, key);
            throw new IllegalArgumentException("코드를 너무 많이 틀렸어요. 처음부터 다시 로그인해 주세요.");
        }
        if (!checkCode(userSn, code)) {
            long left = MAX_TRIES - tries;
            if (left <= 0) redis.delRedis(DB, key);
            throw new IllegalArgumentException(left > 0 ? "코드가 맞지 않아요. (" + left + "번 남음)" : "코드를 너무 많이 틀렸어요. 처음부터 다시 로그인해 주세요.");
        }
        redis.delRedis(DB, key);
        return userSn;
    }

    private void requireCode(long me, String code) {
        if (!isEnabled(me)) throw new IllegalArgumentException("2단계 인증이 꺼져 있어요.");
        long tries = redis.incr(DB, "2fa:mgmt-tries:" + me, 600);
        if (tries > MAX_TRIES) throw new IllegalArgumentException("코드를 너무 많이 틀렸어요. 10분 뒤에 다시 해 주세요.");
        if (!checkCode(me, code)) throw new IllegalArgumentException("코드가 맞지 않아요.");
        redis.delRedis(DB, "2fa:mgmt-tries:" + me);
    }

    /** 인증 앱 코드(6자리) 또는 복구 코드(XXXX-XXXX). 복구 코드는 쓰면 끝 */
    private boolean checkCode(long userSn, String code) {
        String secret = jdbc.queryForObject("SELECT totp_secret FROM knowra_user.tbl_user WHERE user_sn = ?", String.class, userSn);
        if (secret == null || code == null) return false;
        String c = code.trim();
        if (c.replaceAll("\\s", "").matches("\\d{6}")) {
            long k = Totp.verify(secret, c, Instant.now().getEpochSecond());
            return k >= 0 && markUsed(userSn, k);
        }
        String norm = c.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (norm.length() != 8) return false;
        return jdbc.update("UPDATE knowra_user.tbl_user_recovery_code SET used_at = ? WHERE user_sn = ? AND code_hash = ? AND used_at IS NULL",
                LocalDateTime.now(), userSn, sha256(norm)) == 1;
    }

    /** 같은 코드(카운터)는 한 번만 — 처음 쓰면 true */
    private boolean markUsed(long userSn, long counter) {
        return redis.setIfAbsent(DB, "2fa:used:" + userSn + ":" + counter, "1", Totp.STEP_SECONDS * 3L);
    }

    private List<String> newRecoveryCodes(long me) {
        jdbc.update("DELETE FROM knowra_user.tbl_user_recovery_code WHERE user_sn = ?", me);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < RECOVERY_COUNT; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) sb.append(RC_CHARS.charAt(RNG.nextInt(RC_CHARS.length())));
            String raw = sb.toString();
            jdbc.update("INSERT INTO knowra_user.tbl_user_recovery_code (user_sn, code_hash) VALUES (?, ?)", me, sha256(raw));
            out.add(raw.substring(0, 4) + "-" + raw.substring(4));
        }
        return out;
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String qrDataUrl(String text) {
        try {
            var matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 240, 240, Map.of(EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
