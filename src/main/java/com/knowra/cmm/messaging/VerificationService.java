package com.knowra.cmm.messaging;

import com.knowra.cmm.jwt.JwtAuthenticationFilter;
import com.knowra.cmm.service.RedisApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 가입 전 본인 확인 코드 — 이메일·휴대폰 공통. 상태는 전부 Redis(토큰 차단과 같은 DB)에 TTL 로 둔다.
 *
 * <pre>
 *   verif:{channel}:{target}       → 6자리 코드        (10분, 틀리면 5번까지)
 *   verif:{channel}:{target}:tries → 실패 횟수
 *   verif:{channel}:{target}:rl    → 재발송 잠금        (60초)
 *   verif:ok:{channel}:{target}    → 확인 완료 표식     (30분, 가입이 소비한다)
 * </pre>
 *
 * 코드 자체를 응답에 싣지 않는다. 로컬에서 메일·문자가 꺼져 있을 때만
 * {@code knowra.verify.expose-code=true} 로 응답에 {@code devCode} 를 실어 흐름을 돌려 볼 수 있다.
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    public static final String EMAIL = "email";
    public static final String PHONE = "phone";

    private static final int REDIS_DB     = JwtAuthenticationFilter.REDIS_DB;
    private static final int CODE_TTL     = 600;
    private static final int RESEND_LOCK  = 60;
    private static final int OK_TTL       = 1800;
    private static final int MAX_TRIES    = 5;

    private final RedisApiService redis;
    private final SecureRandom random = new SecureRandom();

    @Value("${knowra.verify.expose-code:false}")
    private boolean exposeCode;

    public boolean exposeCode() { return exposeCode; }

    public static String normalize(String channel, String target) {
        if (target == null) return "";
        return EMAIL.equals(channel)
                ? target.trim().toLowerCase(Locale.ROOT)
                : target.replaceAll("[^0-9]", "");
    }

    /** @return 새 코드. 재발송 잠금 중이면 null. */
    public String issue(String channel, String target) {
        String base = key(channel, target);
        if (redis.getRedis(REDIS_DB, base + ":rl") != null) return null;
        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.setRedis(REDIS_DB, base, code, CODE_TTL);
        redis.delRedis(REDIS_DB, base + ":tries");
        redis.setRedis(REDIS_DB, base + ":rl", "1", RESEND_LOCK);
        return code;
    }

    /** 맞으면 확인 표식을 남기고 코드를 지운다. */
    public boolean check(String channel, String target, String code) {
        String base = key(channel, target);
        Object saved = redis.getRedis(REDIS_DB, base);
        if (saved == null) return false;
        Object triesRaw = redis.getRedis(REDIS_DB, base + ":tries");
        int tries = triesRaw == null ? 0 : Integer.parseInt(triesRaw.toString());
        if (tries >= MAX_TRIES) { redis.delRedis(REDIS_DB, base); return false; }
        if (!saved.toString().equals(code == null ? "" : code.trim())) {
            redis.setRedis(REDIS_DB, base + ":tries", String.valueOf(tries + 1), CODE_TTL);
            return false;
        }
        redis.delRedis(REDIS_DB, base);
        redis.delRedis(REDIS_DB, base + ":tries");
        redis.setRedis(REDIS_DB, "verif:ok:" + channel + ":" + target, "1", OK_TTL);
        return true;
    }

    public boolean isVerified(String channel, String target) {
        return redis.getRedis(REDIS_DB, "verif:ok:" + channel + ":" + target) != null;
    }

    public void consume(String channel, String target) {
        redis.delRedis(REDIS_DB, "verif:ok:" + channel + ":" + target);
    }

    private static String key(String channel, String target) {
        return "verif:" + channel + ":" + target;
    }
}
