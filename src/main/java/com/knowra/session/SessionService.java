package com.knowra.session;

import com.knowra.cmm.service.RedisApiService;
import com.knowra.common.util.ComUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 로그인한 기기 = 세션 한 줄. sid 는 액세스·리프레시 토큰에 실린다(JwtProvider).
 *
 * <p>끊기: 줄을 지우고({@link #revoke}) sid 를 Redis 에 액세스 토큰 수명(1시간)만큼 올린다 —
 * 필터({@code JwtAuthenticationFilter})가 그 sid 의 액세스 토큰을 거절하고, 리프레시는 줄이 없어 실패한다.
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final int REDIS_DB = 15;
    private static final String REVOKED = "sid:revoked:";
    private static final int ACCESS_TTL_SEC = 3600;
    private final TblUserSessionRepository repo;
    private final RedisApiService redis;
    private final ApplicationEventPublisher publisher;

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest() : null;
    }

    /** 새 로그인 — 새 sid. */
    @Transactional
    public String start(long userSn) {
        HttpServletRequest req = currentRequest();
        String ua = req == null ? null : req.getHeader("User-Agent");
        TblUserSession s = TblUserSession.builder()
                .sid(UUID.randomUUID().toString()).userSn(userSn)
                .userAgent(ua == null ? null : ua.substring(0, Math.min(300, ua.length())))
                .ip(req == null ? null : ComUtil.extractClientIp(req))
                .build();
        repo.save(s);
        return s.getSid();
    }

    /** 리프레시 — 살아 있는 세션이면 마지막 사용 시각을 갱신하고 true. 지워졌거나 남의 것이면 false. */
    @Transactional
    public boolean touch(String sid, long userSn) {
        if (sid == null) return false;
        TblUserSession s = repo.findById(sid).orElse(null);
        if (s == null || s.getUserSn() != userSn) return false;
        HttpServletRequest req = currentRequest();
        s.setLastSeenAt(LocalDateTime.now());
        if (req != null) s.setIp(ComUtil.extractClientIp(req));
        repo.save(s);
        return true;
    }

    public boolean isRevoked(String sid) {
        return sid != null && redis.getRedis(REDIS_DB, REVOKED + sid) != null;
    }

    /** 이 기기 로그아웃(본인 로그아웃 포함). 남의 sid 면 아무 일도 없다. */
    @Transactional
    public boolean revoke(long userSn, String sid) {
        TblUserSession s = repo.findById(sid).orElse(null);
        if (s == null || s.getUserSn() != userSn) return false;
        repo.delete(s);
        redis.setRedis(REDIS_DB, REVOKED + sid, "1", ACCESS_TTL_SEC);
        publisher.publishEvent(new SessionRevokedEvent(userSn, sid));
        return true;
    }

    /** 이 기기만 남기고 모두 로그아웃. 반환: 끊은 개수 */
    @Transactional
    public int revokeOthers(long userSn, String keepSid) {
        int n = 0;
        for (TblUserSession s : repo.findAllByUserSn(userSn)) {
            if (s.getSid().equals(keepSid)) continue;
            repo.delete(s);
            redis.setRedis(REDIS_DB, REVOKED + s.getSid(), "1", ACCESS_TTL_SEC);
            publisher.publishEvent(new SessionRevokedEvent(userSn, s.getSid()));
            n++;
        }
        return n;
    }

    public List<Map<String, Object>> list(long userSn, String currentSid) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TblUserSession s : repo.findAllByUserSnAndLastSeenAtAfterOrderByLastSeenAtDesc(userSn, LocalDateTime.now().minusDays(30))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sid", s.getSid());
            m.put("device", describe(s.getUserAgent()));
            m.put("ip", maskIp(s.getIp()));
            m.put("createdAt", s.getCreatedAt());
            m.put("lastSeenAt", s.getLastSeenAt());
            m.put("current", s.getSid().equals(currentSid));
            out.add(m);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> !(Boolean) m.get("current")));
        return out;
    }

    /** 사람이 알아볼 이름 — "Chrome · macOS". 정확할 필요는 없고 "내 폰인가"만 알면 된다. */
    static String describe(String ua) {
        if (ua == null || ua.isBlank()) return "알 수 없는 기기";
        String b = ua.contains("Edg/") ? "Edge" : ua.contains("SamsungBrowser") ? "삼성 인터넷" : ua.contains("Whale") ? "웨일"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari"
                : ua.contains("curl/") ? "curl" : "브라우저";
        String os = ua.contains("iPhone") ? "iPhone" : ua.contains("iPad") ? "iPad" : ua.contains("Android") ? "Android"
                : ua.contains("Mac OS X") || ua.contains("Macintosh") ? "macOS" : ua.contains("Windows") ? "Windows"
                : ua.contains("Linux") ? "Linux" : null;
        return os == null ? b : b + " · " + os;
    }

    /** 목록엔 IP 를 다 보이지 않는다 — 대역만으로 "내 집인가"는 충분하다. */
    static String maskIp(String ip) {
        if (ip == null) return null;
        if (ip.contains(".")) { String[] p = ip.split("\\."); return p.length == 4 ? p[0] + "." + p[1] + ".*.*" : ip; }
        if (ip.contains(":")) { String[] p = ip.split(":"); return p.length > 2 ? p[0] + ":" + p[1] + ":…" : ip; }
        return ip;
    }
}
