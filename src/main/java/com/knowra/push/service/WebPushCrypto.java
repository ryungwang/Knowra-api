package com.knowra.push.service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;

/**
 * 웹 푸시 메시지 암호화 — RFC 8291 (aes128gcm, RFC 8188 단일 레코드).
 *
 * <p>외부 라이브러리 없이 JDK 만 쓴다: P-256 ECDH · HMAC-SHA256(HKDF) · AES-128-GCM.
 * 맞는지는 RFC 8291 부록 A 의 테스트 벡터로 검증한다(WebPushCryptoTest) — 바이트 하나만 틀려도
 * 브라우저는 아무 말 없이 버리므로, 눈으로는 절대 못 찾는다.
 */
public final class WebPushCrypto {

    private WebPushCrypto() {}

    private static final Base64.Encoder B64U = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    public static final int RECORD_SIZE = 4096;

    public static String b64u(byte[] b) { return B64U.encodeToString(b); }
    public static byte[] unb64u(String s) { return B64D.decode(s.replace('+', '-').replace('/', '_').replaceAll("=+$", "")); }

    /* ───── 키 ───── */

    static ECParameterSpec p256() {
        try {
            AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
            ap.init(new ECGenParameterSpec("secp256r1"));
            return ap.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    /** 비압축 점(0x04 || X || Y, 65바이트). */
    public static byte[] encodePublic(ECPublicKey k) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        put32(k.getW().getAffineX(), out, 1);
        put32(k.getW().getAffineY(), out, 33);
        return out;
    }

    public static byte[] encodePrivate(ECPrivateKey k) { byte[] out = new byte[32]; put32(k.getS(), out, 0); return out; }

    public static ECPublicKey decodePublic(byte[] raw) {
        if (raw.length != 65 || raw[0] != 0x04) throw new IllegalArgumentException("P-256 비압축 공개키가 아니다");
        try {
            ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)), new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, p256()));
        } catch (GeneralSecurityException e) { throw new IllegalArgumentException("공개키를 읽지 못했다", e); }
    }

    public static ECPrivateKey decodePrivate(byte[] raw) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), p256()));
        } catch (GeneralSecurityException e) { throw new IllegalArgumentException("개인키를 읽지 못했다", e); }
    }

    /** 공개키를 개인키에서 다시 계산할 방법이 JDK 에 없어서, 키 쌍은 둘 다 받는다. */
    public static KeyPair keyPair(byte[] pub, byte[] priv) { return new KeyPair(decodePublic(pub), decodePrivate(priv)); }

    private static void put32(BigInteger v, byte[] out, int off) {
        byte[] b = v.toByteArray();                         // 부호 바이트가 붙거나 짧을 수 있다
        int len = Math.min(b.length, 32);
        System.arraycopy(b, b.length - len, out, off + 32 - len, len);
    }

    /* ───── 암호화 ───── */

    /** 운영 경로: 서버 임시 키·salt 를 새로 만든다. */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, generateKeyPair(), salt);
    }

    /** 결정적 경로(테스트 벡터용): 서버 키 쌍·salt 를 밖에서 준다. */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair as, byte[] salt) {
        try {
            byte[] asPublic = encodePublic((ECPublicKey) as.getPublic());

            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(as.getPrivate());
            ka.doPhase(decodePublic(uaPublic), true);
            byte[] ecdh = ka.generateSecret();

            // IKM = HKDF(salt=auth, ikm=ecdh, info="WebPush: info\0"||ua_public||as_public, L=32)
            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
            byte[] ikm = expand(hmac(authSecret, ecdh), keyInfo, 32);

            byte[] prk = hmac(salt, ikm);
            byte[] cek = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            // 단일(=마지막) 레코드: 본문 뒤에 구분자 0x02
            byte[] padded = concat(plaintext, new byte[]{0x02});
            if (padded.length + 16 > RECORD_SIZE) throw new IllegalArgumentException("푸시 본문이 너무 크다(4KB 미만)");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ct = c.doFinal(padded);

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
            return concat(header.array(), ct);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("푸시 암호화 실패", e);
        }
    }

    /** 단일 블록 HKDF-Expand(L ≤ 32): T(1) = HMAC(PRK, info || 0x01). */
    private static byte[] expand(byte[] prk, byte[] info, int len) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[]{0x01})), len);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(data);
    }

    static byte[] concat(byte[]... parts) {
        int n = 0; for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n]; int off = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, off, p.length); off += p.length; }
        return out;
    }
}
