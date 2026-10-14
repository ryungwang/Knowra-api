package com.knowra.user.twofactor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * TOTP(RFC 6238) — 인증 앱(Google Authenticator·1Password 등)이 쓰는 6자리 30초 코드. HMAC-SHA1.
 * 라이브러리 없이 둔다: 표준이 짧고, 검증용 테스트 벡터(TotpTest)가 RFC 에 있다.
 */
public final class Totp {

    public static final int STEP_SECONDS = 30, DIGITS = 6;
    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RNG = new SecureRandom();

    private Totp() {}

    /** 160비트 비밀 — base32(32글자) */
    public static String newSecret() {
        byte[] b = new byte[20];
        RNG.nextBytes(b);
        return base32(b);
    }

    public static long counterAt(long epochSeconds) { return Math.floorDiv(epochSeconds, STEP_SECONDS); }

    public static String code(String secretBase32, long counter) {
        return code(base32Decode(secretBase32), counter, DIGITS);
    }

    static String code(byte[] key, long counter, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            int mod = (int) Math.pow(10, digits);
            return String.format("%0" + digits + "d", bin % mod);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 맞으면 맞은 카운터, 아니면 -1. 앞뒤 한 칸(±30초)까지 받는다 — 휴대폰 시계가 조금 어긋나도 통과하게.
     * 비교는 길이 고정 상수 시간 비교(타이밍으로 자리를 맞혀 가지 못하게).
     */
    public static long verify(String secretBase32, String input, long nowEpochSeconds) {
        if (input == null) return -1;
        String c = input.replaceAll("\\s", "");
        if (!c.matches("\\d{" + DIGITS + "}")) return -1;
        byte[] key = base32Decode(secretBase32);
        long now = counterAt(nowEpochSeconds);
        for (long k = now - 1; k <= now + 1; k++) {
            if (java.security.MessageDigest.isEqual(code(key, k, DIGITS).getBytes(StandardCharsets.US_ASCII), c.getBytes(StandardCharsets.US_ASCII))) return k;
        }
        return -1;
    }

    /** 인증 앱이 QR 로 읽는 주소 */
    public static String uri(String issuer, String account, String secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer) + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buf = 0, bits = 0;
        for (byte b : data) {
            buf = (buf << 8) | (b & 0xff); bits += 8;
            while (bits >= 5) { sb.append(B32.charAt((buf >> (bits - 5)) & 31)); bits -= 5; }
        }
        if (bits > 0) sb.append(B32.charAt((buf << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        String in = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(in.length() * 5 / 8);
        int buf = 0, bits = 0;
        for (char ch : in.toCharArray()) {
            int v = B32.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("base32 가 아닌 글자");
            buf = (buf << 5) | v; bits += 5;
            if (bits >= 8) { out.put((byte) ((buf >> (bits - 8)) & 0xff)); bits -= 8; }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }
}
