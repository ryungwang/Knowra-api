package com.knowra.cmm.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

/**
 * 문자 발송. 공급자는 {@code knowra.sms.provider} 로 고른다.
 * <ul>
 *   <li>{@code none}(로컬 기본) — 보내지 않고 로그에만 남긴다.</li>
 *   <li>{@code solapi} — Solapi(구 CoolSMS) REST. 필요한 값: api-key, api-secret, from(발신번호, 사전 등록).
 *       인증은 HMAC-SHA256 헤더(date + salt 를 secret 으로 서명).</li>
 * </ul>
 * 켜져 있는데 보내기에 실패하면 예외를 던진다.
 */
@Slf4j
@Service
public class SmsService {

    private static final String SOLAPI_URL = "https://api.solapi.com/messages/v4/send";

    private final String provider;
    private final String apiKey;
    private final String apiSecret;
    private final String from;
    private final RestTemplate rest = new RestTemplate();
    private final SecureRandom random = new SecureRandom();

    public SmsService(@Value("${knowra.sms.provider:none}") String provider,
                      @Value("${knowra.sms.api-key:}") String apiKey,
                      @Value("${knowra.sms.api-secret:}") String apiSecret,
                      @Value("${knowra.sms.from:}") String from) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.from = from;
    }

    public boolean isEnabled() { return !"none".equalsIgnoreCase(provider); }

    public void send(String to, String text) {
        if (!isEnabled()) {
            log.info("[SMS(dry-run)] to={}\n{}", to, text);
            return;
        }
        if (!"solapi".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("지원하지 않는 SMS 공급자: " + provider);
        }
        if (apiKey.isBlank() || apiSecret.isBlank() || from.isBlank()) {
            throw new IllegalStateException("SMS 설정이 비어 있습니다 (knowra.sms.api-key / api-secret / from)");
        }
        String date = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        byte[] saltBytes = new byte[16]; random.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        String signature = hmacSha256(apiSecret, date + salt);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Authorization", "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + signature);
        Map<String, Object> body = Map.of("message", Map.of("to", to.replace("-", ""), "from", from, "text", text));
        ResponseEntity<String> res = rest.exchange(SOLAPI_URL, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        if (!res.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("SMS 발송 실패: " + res.getStatusCode());
        }
    }

    private static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 서명 실패", e);
        }
    }
}
