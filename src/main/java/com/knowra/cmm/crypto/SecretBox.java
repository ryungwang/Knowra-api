package com.knowra.cmm.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * DB 에 넣는 비밀값(지금은 2단계 인증 키) 암호화 — AES-256-GCM.
 *
 * <p>저장 꼴은 {@code v1:<base64(iv 12바이트 + 암호문 + 태그)>}. 「v1:」 로 시작하지 않으면 예전 평문으로 보고 그대로 돌려준다
 * (기동 때 {@link com.knowra.user.twofactor.TotpSecretMigrator} 가 남은 평문을 옮긴다).
 *
 * <p>aad 에 주인(예: "totp:58")을 묶는다 — DB 를 고칠 수 있는 사람이 남의 암호문을 자기 줄에 복사해도 풀리지 않는다.
 *
 * <p>키는 base64 32바이트({@code knowra.crypto.secret-key}). 잃으면 저장된 값을 되살릴 수 없다 — 2단계 인증을 쓰는 사람은
 * 복구 코드로 들어와 다시 설정해야 한다. 키를 바꾸려면 v2 를 더하고 둘 다 읽게 한 뒤 옮긴다.
 */
@Component
public class SecretBox {

    private static final String PREFIX = "v1:";
    private static final int IV_LEN = 12, TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    private final SecretKeySpec key;

    public SecretBox(@Value("${knowra.crypto.secret-key}") String base64Key) {
        byte[] k;
        try { k = Base64.getDecoder().decode(base64Key.trim()); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("knowra.crypto.secret-key 는 base64 여야 합니다 (openssl rand -base64 32)"); }
        if (k.length != 32) throw new IllegalStateException("knowra.crypto.secret-key 는 32바이트여야 합니다 (openssl rand -base64 32)");
        this.key = new SecretKeySpec(k, "AES");
    }

    public static boolean isSealed(String value) { return value != null && value.startsWith(PREFIX); }

    public String seal(String plain, String aad) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_LEN];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(IV_LEN + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    /** 평문(예전 값)은 그대로. 풀리지 않으면(키가 다르거나 aad 가 다름) 예외 */
    public String open(String value, String aad) {
        if (value == null || !isSealed(value)) return value;
        try {
            byte[] all = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(all, IV_LEN, all.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("비밀값을 풀 수 없습니다 — 암호화 키가 바뀌었는지 확인하세요", e);
        }
    }
}
