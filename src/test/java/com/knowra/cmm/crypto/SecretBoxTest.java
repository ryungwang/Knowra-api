package com.knowra.cmm.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SecretBoxTest {

    private static String key(int fill) { byte[] k = new byte[32]; java.util.Arrays.fill(k, (byte) fill); return Base64.getEncoder().encodeToString(k); }

    private final SecretBox box = new SecretBox(key(7));

    @Test
    void roundTrip() {
        String sealed = box.seal("JBSWY3DPEHPK3PXP", "totp:58");
        assertTrue(SecretBox.isSealed(sealed));
        assertFalse(sealed.contains("JBSWY3DPEHPK3PXP"));
        assertEquals("JBSWY3DPEHPK3PXP", box.open(sealed, "totp:58"));
    }

    @Test
    void sameInputDifferentCiphertext() {
        // 무작위 IV — 같은 키를 쓰는 두 사람이 DB 에서 같아 보이면 안 된다
        assertNotEquals(box.seal("A", "totp:1"), box.seal("A", "totp:1"));
    }

    @Test
    void copiedToAnotherUserFails() {
        String sealed = box.seal("JBSWY3DPEHPK3PXP", "totp:58");
        assertThrows(IllegalStateException.class, () -> box.open(sealed, "totp:1"));
    }

    @Test
    void wrongKeyFails() {
        String sealed = box.seal("JBSWY3DPEHPK3PXP", "totp:58");
        assertThrows(IllegalStateException.class, () -> new SecretBox(key(8)).open(sealed, "totp:58"));
    }

    @Test
    void tamperedFails() {
        String sealed = box.seal("JBSWY3DPEHPK3PXP", "totp:58");
        char last = sealed.charAt(sealed.length() - 3);
        String bad = sealed.substring(0, sealed.length() - 3) + (last == 'A' ? 'B' : 'A') + sealed.substring(sealed.length() - 2);
        assertThrows(IllegalStateException.class, () -> box.open(bad, "totp:58"));
    }

    @Test
    void legacyPlaintextPassesThrough() {
        assertEquals("JBSWY3DPEHPK3PXP", box.open("JBSWY3DPEHPK3PXP", "totp:58"));
        assertNull(box.open(null, "totp:58"));
    }

    @Test
    void rejectsBadKeys() {
        assertThrows(IllegalStateException.class, () -> new SecretBox(Base64.getEncoder().encodeToString(new byte[16])));
        assertThrows(IllegalStateException.class, () -> new SecretBox("not base64 !!"));
    }
}
