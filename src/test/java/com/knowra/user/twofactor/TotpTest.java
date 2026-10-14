package com.knowra.user.twofactor;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TotpTest {

    // RFC 6238 부록 B — SHA1, 비밀 "12345678901234567890", 8자리
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void rfc6238Vectors() {
        assertEquals("94287082", Totp.code(RFC_KEY, Totp.counterAt(59), 8));
        assertEquals("07081804", Totp.code(RFC_KEY, Totp.counterAt(1111111109), 8));
        assertEquals("14050471", Totp.code(RFC_KEY, Totp.counterAt(1111111111), 8));
        assertEquals("89005924", Totp.code(RFC_KEY, Totp.counterAt(1234567890), 8));
        assertEquals("69279037", Totp.code(RFC_KEY, Totp.counterAt(2000000000), 8));
    }

    @Test
    void base32RoundTrip() {
        String b = Totp.base32(RFC_KEY);
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", b);
        assertArrayEquals(RFC_KEY, Totp.base32Decode(b));
    }

    @Test
    void verifyAcceptsOneStepDriftOnly() {
        String secret = Totp.newSecret();
        long now = 1_800_000_000L;
        long c = Totp.counterAt(now);
        assertEquals(c, Totp.verify(secret, Totp.code(secret, c), now));
        assertEquals(c - 1, Totp.verify(secret, Totp.code(secret, c - 1), now));
        assertEquals(c + 1, Totp.verify(secret, Totp.code(secret, c + 1), now));
        assertEquals(-1, Totp.verify(secret, Totp.code(secret, c - 2), now));
        assertEquals(-1, Totp.verify(secret, "12345", now));
        assertEquals(-1, Totp.verify(secret, null, now));
        assertEquals(c, Totp.verify(secret, " " + Totp.code(secret, c).substring(0, 3) + " " + Totp.code(secret, c).substring(3), now));
    }
}
