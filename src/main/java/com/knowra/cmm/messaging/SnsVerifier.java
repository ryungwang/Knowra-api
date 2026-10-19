package com.knowra.cmm.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Amazon SNS 메시지 서명 확인. 이 창구는 누구나 부를 수 있어서, 확인하지 않으면 아무나 남의 주소를 「반송」으로 적어
 * 메일을 막을 수 있다.
 *
 * <p>SNS 는 정해진 칸을 「이름\n값\n」으로 이어 붙인 글을 서명한다(SignatureVersion 1 = SHA1, 2 = SHA256).
 * 인증서는 SigningCertURL 에서 받는데, <b>AWS 의 SNS 주소일 때만</b> 받는다 — 아니면 공격자가 자기 인증서로 서명할 수 있다.
 * https://docs.aws.amazon.com/sns/latest/dg/sns-verify-signature-of-message.html
 */
@Component
public class SnsVerifier {

    /** sns.<지역>.amazonaws.com(.cn) — 다른 호스트의 인증서·구독 주소는 믿지 않는다 */
    static final Pattern SNS_HOST = Pattern.compile("^sns\\.[a-z0-9-]+\\.amazonaws\\.com(\\.cn)?$");

    private static final List<String> NOTIFICATION = List.of("Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type");
    private static final List<String> SUBSCRIPTION = List.of("Message", "MessageId", "SubscribeURL", "Timestamp", "Token", "TopicArn", "Type");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, PublicKey> certs = new ConcurrentHashMap<>();

    /** AWS 의 SNS 주소인가 — https 이고 호스트가 sns.*.amazonaws.com */
    public static boolean isSnsUrl(String url) {
        try {
            URI u = URI.create(url);
            return "https".equals(u.getScheme()) && u.getHost() != null && SNS_HOST.matcher(u.getHost()).matches() && u.getPort() == -1;
        } catch (Exception e) { return false; }
    }

    /** 서명할 때 쓴 글 — 칸이 없으면(알림의 Subject 등) 건너뛴다 */
    static String stringToSign(JsonNode m) {
        String type = m.path("Type").asText();
        List<String> keys = "Notification".equals(type) ? NOTIFICATION : SUBSCRIPTION;
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            JsonNode v = m.get(k);
            if (v == null || v.isNull()) continue;
            sb.append(k).append('\n').append(v.asText()).append('\n');
        }
        return sb.toString();
    }

    public boolean verify(JsonNode m) {
        String certUrl = m.path("SigningCertURL").asText("");
        if (!isSnsUrl(certUrl) || !certUrl.endsWith(".pem")) return false;
        try { return verify(m, certs.computeIfAbsent(certUrl, this::fetch)); }
        catch (RuntimeException e) { return false; }
    }

    static boolean verify(JsonNode m, PublicKey key) {
        String alg = switch (m.path("SignatureVersion").asText()) {
            case "1" -> "SHA1withRSA";
            case "2" -> "SHA256withRSA";
            default -> null;
        };
        if (alg == null || key == null) return false;
        try {
            Signature s = Signature.getInstance(alg);
            s.initVerify(key);
            s.update(stringToSign(m).getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.getDecoder().decode(m.path("Signature").asText("")));
        } catch (Exception e) { return false; }
    }

    private PublicKey fetch(String url) {
        try {
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) throw new IllegalStateException("cert " + r.statusCode());
            X509Certificate c = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(r.body()));
            c.checkValidity();
            return c.getPublicKey();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** 구독 확인 — SubscribeURL 을 한 번 연다(AWS 주소일 때만) */
    public boolean confirm(String subscribeUrl) {
        if (!isSnsUrl(subscribeUrl)) return false;
        try {
            return http.send(HttpRequest.newBuilder(URI.create(subscribeUrl)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) { return false; }
    }
}
