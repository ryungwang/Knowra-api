package com.knowra.push.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowra.push.entity.TblPushSub;
import com.knowra.push.repository.TblPushSubRepository;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 웹 푸시 발송. 메시지는 기기마다 그 기기 키로 암호화해(WebPushCrypto) 브라우저 푸시 서비스로 보낸다.
 *
 * <p><b>엔드포인트 허용 목록.</b> 구독 주소는 클라이언트가 보내는 URL 이고 서버가 거기로 POST 한다 —
 * 막지 않으면 내부망 주소를 구독으로 등록해 서버를 대신 찌르게 할 수 있다(SSRF). 그래서 https 이고
 * 알려진 푸시 서비스 호스트인 것만 받는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebPushService {

    /** 브라우저 푸시 서비스 — Chrome·Edge(FCM), Firefox(Mozilla autopush), Safari(Apple), 구 Edge(WNS). */
    private static final List<String> ALLOWED_HOST_SUFFIXES = List.of(
            "fcm.googleapis.com", "push.services.mozilla.com", "push.apple.com", "notify.windows.com");

    private final TblPushSubRepository repo;
    private final VapidKeys vapid;
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public static boolean allowedEndpoint(String endpoint) {
        try {
            URI u = URI.create(endpoint);
            if (!"https".equals(u.getScheme()) || u.getHost() == null) return false;
            String h = u.getHost().toLowerCase(Locale.ROOT);
            return ALLOWED_HOST_SUFFIXES.stream().anyMatch(s -> h.equals(s) || h.endsWith("." + s));
        } catch (Exception e) { return false; }
    }

    @Transactional
    public void subscribe(long userSn, String endpoint, String p256dh, String auth, String ua) {
        if (!vapid.enabled()) throw new IllegalStateException("푸시가 꺼져 있어요.");
        if (!allowedEndpoint(endpoint)) throw new IllegalArgumentException("지원하지 않는 푸시 주소예요.");
        try {
            WebPushCrypto.decodePublic(WebPushCrypto.unb64u(p256dh));   // 형식이 틀리면 여기서 거절
            if (WebPushCrypto.unb64u(auth).length != 16) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("구독 정보가 올바르지 않아요. 알림을 껐다가 다시 켜 주세요.");
        }
        TblPushSub s = repo.findByEndpoint(endpoint).orElseGet(() -> TblPushSub.builder().endpoint(endpoint).build());
        s.setUserSn(userSn); s.setP256dh(p256dh); s.setAuth(auth);
        s.setUserAgent(ua == null ? null : ua.substring(0, Math.min(ua.length(), 300)));
        repo.save(s);
    }

    @Transactional
    public void unsubscribe(long userSn, String endpoint) {
        repo.findByEndpoint(endpoint).filter(s -> s.getUserSn() == userSn).ifPresent(repo::delete);
    }

    public long count(long userSn) { return repo.countByUserSn(userSn); }

    /**
     * 한 사람의 모든 기기로 보낸다. 반환: 성공한 기기 수. 죽은 구독(404·410)은 지운다.
     * payload 는 서비스워커(sw.js)가 읽는 JSON — title, body, url, tag.
     */
    public int sendToUser(long userSn, Map<String, Object> payload) {
        if (!vapid.enabled()) return 0;
        List<TblPushSub> subs = repo.findAllByUserSn(userSn);
        if (subs.isEmpty()) return 0;
        byte[] json;
        try { json = om.writeValueAsBytes(payload); } catch (Exception e) { return 0; }
        int ok = 0;
        for (TblPushSub s : subs) {
            try {
                int code = sendOne(s, json);
                if (code >= 200 && code < 300) { ok++; s.setLastOkDt(LocalDateTime.now()); repo.save(s); }
                else if (code == 404 || code == 410) { repo.delete(s); log.info("push: 만료된 구독 정리 sub={}", s.getSubSn()); }
                else log.warn("push: 푸시 서비스 응답 {} sub={}", code, s.getSubSn());
            } catch (Exception e) {
                log.warn("push: 발송 실패 sub={} {}", s.getSubSn(), e.toString());
            }
        }
        return ok;
    }

    private int sendOne(TblPushSub s, byte[] json) throws Exception {
        if (!allowedEndpoint(s.getEndpoint())) return 410;   // 예전에 들어온 이상한 주소는 정리
        byte[] body = WebPushCrypto.encrypt(json, WebPushCrypto.unb64u(s.getP256dh()), WebPushCrypto.unb64u(s.getAuth()));
        URI u = URI.create(s.getEndpoint());
        String aud = u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        String jwt = Jwts.builder()
                .header().add("typ", "JWT").and()
                .audience().single(aud)
                .expiration(new Date(System.currentTimeMillis() + 12 * 3600_000L))
                .subject(vapid.subject())
                .signWith(vapid.pair().getPrivate(), Jwts.SIG.ES256)
                .compact();
        HttpRequest req = HttpRequest.newBuilder(u)
                .timeout(Duration.ofSeconds(10))
                .header("TTL", "86400")
                .header("Urgency", "normal")
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", "vapid t=" + jwt + ", k=" + vapid.publicKey())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
