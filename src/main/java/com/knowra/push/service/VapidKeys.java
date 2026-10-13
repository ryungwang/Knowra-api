package com.knowra.push.service;

import com.knowra.cmm.service.RedisApiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

/**
 * VAPID 키 쌍(RFC 8292) — 푸시 서비스에 "이 서버가 보낸 것"을 증명한다.
 *
 * <p>운영: {@code VAPID_PUBLIC_KEY}·{@code VAPID_PRIVATE_KEY} 환경변수(base64url 원시 키). 없으면
 * 푸시를 <b>끈다</b> — 공개키 API 가 null 을 주고 프론트는 토글을 숨긴다.
 * ⚠️ 키를 바꾸면 이미 받은 구독이 전부 무효가 된다(브라우저가 구독할 때 공개키를 묶어 둔다).
 *
 * <p>로컬: {@code knowra.push.dev-generate=true} 면 처음 한 번 만들어 Redis 에 둔다 —
 * 서버를 다시 띄워도 같은 키라 구독이 살아 있다.
 *
 * <p>⚠️ <b>생성자에서 Redis 를 건드리지 않는다.</b> RedisConfig 가 연결 팩토리를 @PostConstruct 에서
 * 손으로 등록하는데, 생성 시점에 그걸 당겨 쓰면 등록 순서가 바뀌어 스프링 자동 설정의 기본 팩토리와
 * 겹쳐 기동이 실패했다("required a single bean, but 2 were found"). 그래서 처음 쓸 때 읽는다.
 */
@Component
public class VapidKeys {

    private static final String REDIS_KEY = "push:vapid";
    private final RedisApiService redis;
    private final String cfgPub, cfgPriv, subject;
    private final boolean devGenerate;

    private volatile boolean loaded;
    private KeyPair pair;
    private String publicB64;

    public VapidKeys(RedisApiService redis,
                     @Value("${knowra.push.vapid.public-key:}") String pub,
                     @Value("${knowra.push.vapid.private-key:}") String priv,
                     @Value("${knowra.push.vapid.subject:mailto:admin@knowra.local}") String subject,
                     @Value("${knowra.push.dev-generate:false}") boolean devGenerate) {
        this.redis = redis; this.cfgPub = pub; this.cfgPriv = priv; this.subject = subject; this.devGenerate = devGenerate;
    }

    private void load() {
        if (loaded) return;
        synchronized (this) {
            if (loaded) return;
            KeyPair kp = null;
            if (StringUtils.hasText(cfgPub) && StringUtils.hasText(cfgPriv)) {
                kp = WebPushCrypto.keyPair(WebPushCrypto.unb64u(cfgPub), WebPushCrypto.unb64u(cfgPriv));
            } else if (devGenerate) {
                Object saved = redis.getRedis(15, REDIS_KEY);
                if (saved != null && String.valueOf(saved).contains(".")) {
                    String[] p = String.valueOf(saved).split("\\.");
                    kp = WebPushCrypto.keyPair(WebPushCrypto.unb64u(p[0]), WebPushCrypto.unb64u(p[1]));
                } else {
                    kp = WebPushCrypto.generateKeyPair();
                    redis.setRedis(15, REDIS_KEY, WebPushCrypto.b64u(WebPushCrypto.encodePublic((ECPublicKey) kp.getPublic()))
                            + "." + WebPushCrypto.b64u(WebPushCrypto.encodePrivate((ECPrivateKey) kp.getPrivate())), null);
                }
            }
            pair = kp;
            publicB64 = kp == null ? null : WebPushCrypto.b64u(WebPushCrypto.encodePublic((ECPublicKey) kp.getPublic()));
            loaded = true;
        }
    }

    public boolean enabled() { load(); return pair != null; }
    public KeyPair pair() { load(); return pair; }
    public String publicKey() { load(); return publicB64; }
    public String subject() { return subject; }
}
