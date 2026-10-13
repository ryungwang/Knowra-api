package com.knowra.push.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import static com.knowra.push.service.WebPushCrypto.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 웹 푸시 암호화 — RFC 8291 부록 A 벡터와, 브라우저 쪽 복호화를 흉내 낸 왕복. */
class WebPushCryptoTest {

    // RFC 8291 Appendix A
    static final String PLAINTEXT  = "When I grow up, I want to be a watermelon";
    static final String AS_PUBLIC  = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    static final String UA_PUBLIC  = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    static final String AUTH       = "BTBZMqHH6r4Tts7J_aSIgg";
    static final String SALT       = "DGv6ra1nlYgDCS1FRnbzlw";
    static final String EXPECTED   = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void RFC8291_부록A_벡터와_바이트까지_같다() {
        KeyPair as = keyPair(unb64u(AS_PUBLIC), unb64u(AS_PRIVATE));
        byte[] body = encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8), unb64u(UA_PUBLIC), unb64u(AUTH), as, unb64u(SALT));
        assertThat(b64u(body)).isEqualTo(EXPECTED);
    }

    @Test
    void 공개키_인코딩은_왕복된다() {
        KeyPair kp = generateKeyPair();
        byte[] raw = encodePublic((ECPublicKey) kp.getPublic());
        assertThat(raw).hasSize(65);
        assertThat(encodePublic(decodePublic(raw))).isEqualTo(raw);
    }

    /** 무작위 키·salt 로 암호화한 것을 브라우저처럼 풀어 본다 — 운영 경로(encrypt 3인자) 검증. */
    @Test
    void 브라우저_쪽에서_풀린다() throws Exception {
        KeyPair ua = generateKeyPair();
        byte[] uaPub = encodePublic((ECPublicKey) ua.getPublic());
        byte[] auth = new byte[16]; new java.security.SecureRandom().nextBytes(auth);
        byte[] msg = "{\"title\":\"knowra\",\"body\":\"안녕\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = encrypt(msg, uaPub, auth);

        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        int idlen = body[20] & 0xff;
        byte[] asPub = Arrays.copyOfRange(body, 21, 21 + idlen);
        byte[] ct = Arrays.copyOfRange(body, 21 + idlen, body.length);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ua.getPrivate()); ka.doPhase(decodePublic(asPub), true);
        byte[] ecdh = ka.generateSecret();
        byte[] ikm = Arrays.copyOf(h(h(auth, ecdh), concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPub, asPub, new byte[]{1})), 32);
        byte[] prk = h(salt, ikm);
        byte[] cek = Arrays.copyOf(h(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(h(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = c.doFinal(ct);

        assertThat(plain[plain.length - 1]).isEqualTo((byte) 0x02);
        assertThat(Arrays.copyOf(plain, plain.length - 1)).isEqualTo(msg);
    }

    private static byte[] h(byte[] k, byte[] d) throws Exception {
        Mac m = Mac.getInstance("HmacSHA256"); m.init(new SecretKeySpec(k, "HmacSHA256")); return m.doFinal(d);
    }
}
