package com.knowra.user.sns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowra.cmm.service.RedisApiService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

/**
 * 소셜 로그인 — 제공자(구글·카카오·네이버·애플)에게 신원을 확인받는 부분만 맡는다.
 * 유저 조회·가입·토큰 발급은 {@code AuthService} 몫.
 *
 * <p><b>키는 서버에만 둔다.</b> 프론트는 {@link #providers()} 가 만들어 준 인가 URL 로 보내기만 하고,
 * 돌아온 code/토큰을 서버에 넘긴다. 제공자가 하나 늘면 여기 {@code fetchIdentity} 분기와
 * 설정 키만 늘면 된다.
 *
 * <p><b>가입 티켓.</b> 신원 확인이 끝났는데 계정이 없으면 프로필 입력이 남는다. 그 사이 제공자
 * 토큰을 다시 들고 다니지 않도록 신원을 Redis 에 15분 짜리 티켓으로 묶어 두고, 가입 요청은
 * 티켓만 낸다 — 가입 폼이 이메일·snsId 를 직접 보내게 두면 아무나 남의 이메일로 SNS 계정을
 * 만들 수 있다(예전 구글 가입이 그랬다).
 */
@Service
public class SnsAuthService {

    public static final String GOOGLE = "GOOGLE", KAKAO = "KAKAO", NAVER = "NAVER", APPLE = "APPLE";
    public static final int REDIS_DB = 15;
    private static final int TICKET_TTL_SEC = 15 * 60;
    private static final String TICKET_PREFIX = "sns:signup:";

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();
    private final RedisApiService redis;

    private final String frontBase;
    private final String googleClientId;
    private final String kakaoClientId, kakaoClientSecret;
    private final String naverClientId, naverClientSecret;
    private final String appleClientId;

    /** 애플 공개키(JWKS) 캐시 — 한 시간. 키가 회전하면 kid 가 안 맞아 다시 받는다. */
    private volatile JwkSet appleKeys;
    private volatile long appleKeysAt;

    public SnsAuthService(RedisApiService redis,
                          @Value("${knowra.front.base-url:http://localhost:5111}") String frontBase,
                          @Value("${knowra.oauth.google.client-id:}") String googleClientId,
                          @Value("${knowra.oauth.kakao.client-id:}") String kakaoClientId,
                          @Value("${knowra.oauth.kakao.client-secret:}") String kakaoClientSecret,
                          @Value("${knowra.oauth.naver.client-id:}") String naverClientId,
                          @Value("${knowra.oauth.naver.client-secret:}") String naverClientSecret,
                          @Value("${knowra.oauth.apple.client-id:}") String appleClientId) {
        this.redis = redis;
        this.frontBase = frontBase.replaceAll("/+$", "");
        this.googleClientId = googleClientId;
        this.kakaoClientId = kakaoClientId; this.kakaoClientSecret = kakaoClientSecret;
        this.naverClientId = naverClientId; this.naverClientSecret = naverClientSecret;
        this.appleClientId = appleClientId;
    }

    /* ───────────── 프론트가 쓸 제공자 목록 ───────────── */

    public String redirectUri(String provider) { return frontBase + "/auth/" + provider.toLowerCase(Locale.ROOT) + "/callback"; }

    /** 설정된 제공자만 돌려준다. 프론트는 이 목록대로 버튼을 그리고 URL 로 보낸다. state 는 프론트가 붙인다. */
    public List<Map<String, Object>> providers() {
        List<Map<String, Object>> list = new ArrayList<>();
        if (StringUtils.hasText(googleClientId)) {
            list.add(Map.of("provider", GOOGLE, "flow", "implicit",
                    "authorizeUrl", "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + enc(googleClientId)
                            + "&redirect_uri=" + enc(redirectUri(GOOGLE)) + "&response_type=token"
                            + "&scope=" + enc("https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile")));
        }
        if (StringUtils.hasText(kakaoClientId)) {
            list.add(Map.of("provider", KAKAO, "flow", "code",
                    "authorizeUrl", "https://kauth.kakao.com/oauth/authorize?client_id=" + enc(kakaoClientId)
                            + "&redirect_uri=" + enc(redirectUri(KAKAO)) + "&response_type=code&scope=" + enc("profile_nickname profile_image account_email")));
        }
        if (StringUtils.hasText(naverClientId)) {
            list.add(Map.of("provider", NAVER, "flow", "code",
                    "authorizeUrl", "https://nid.naver.com/oauth2.0/authorize?response_type=code&client_id=" + enc(naverClientId)
                            + "&redirect_uri=" + enc(redirectUri(NAVER))));
        }
        if (StringUtils.hasText(appleClientId)) {
            // 애플은 JS SDK 팝업(usePopup) — id_token 이 브라우저로 바로 온다. redirectUri 는 등록용.
            list.add(Map.of("provider", APPLE, "flow", "apple", "clientId", appleClientId, "redirectUri", redirectUri(APPLE)));
        }
        return list;
    }

    /* ───────────── 신원 확인 ───────────── */

    /**
     * params — GOOGLE: accessToken / KAKAO·NAVER: code(+state) / APPLE: idToken(+userName).
     * 실패하면 사용자에게 보여 줄 수 있는 메시지로 {@link IllegalArgumentException}.
     */
    public SnsIdentity fetchIdentity(String provider, Map<String, String> params) {
        return switch (provider) {
            case GOOGLE -> google(params.get("accessToken"));
            case KAKAO  -> kakao(params.get("code"));
            case NAVER  -> naver(params.get("code"), params.get("state"));
            case APPLE  -> apple(params.get("idToken"), params.get("userName"));
            default -> throw new IllegalArgumentException("지원하지 않는 로그인 방식이에요.");
        };
    }

    /**
     * ⚠️ 액세스 토큰이 <b>우리 앱에 발급된 것</b>인지(aud/azp == googleClientId) 먼저 본다. 예전엔 userinfo 만
     * 불러서, 다른 사이트가 「구글로 로그인」으로 받아 둔 피해자 토큰을 들고 오면 그 사람으로 로그인됐다.
     */
    @SuppressWarnings("unchecked")
    private SnsIdentity google(String accessToken) {
        if (!StringUtils.hasText(googleClientId)) throw new IllegalArgumentException("구글 로그인이 아직 준비되지 않았어요.");
        if (!StringUtils.hasText(accessToken)) throw new IllegalArgumentException("구글 인증 정보를 받지 못했어요.");
        Map<String, Object> ti = getJson("https://oauth2.googleapis.com/tokeninfo?access_token=" + enc(accessToken), null);
        if (ti == null || !(googleClientId.equals(str(ti.get("aud"))) || googleClientId.equals(str(ti.get("azp")))))
            throw new IllegalArgumentException("구글 인증 정보를 확인하지 못했어요. 다시 시도해 주세요.");
        Map<String, Object> info = getJson("https://www.googleapis.com/oauth2/v1/userinfo?access_token=" + enc(accessToken), null);
        if (info == null || info.get("id") == null) throw new IllegalArgumentException("구글 사용자 정보를 가져오지 못했어요.");
        if (ti.get("sub") != null && !str(ti.get("sub")).equals(str(info.get("id"))))
            throw new IllegalArgumentException("구글 인증 정보를 확인하지 못했어요. 다시 시도해 주세요.");
        boolean verified = Boolean.TRUE.equals(info.get("verified_email")) || "true".equals(str(ti.get("email_verified")));
        return new SnsIdentity(GOOGLE, str(info.get("id")), str(info.get("email")), str(info.get("name")), str(info.get("picture")), verified);
    }

    @SuppressWarnings("unchecked")
    private SnsIdentity kakao(String code) {
        if (!StringUtils.hasText(kakaoClientId)) throw new IllegalArgumentException("카카오 로그인이 아직 준비되지 않았어요.");
        if (!StringUtils.hasText(code)) throw new IllegalArgumentException("카카오 인증 정보를 받지 못했어요.");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code"); form.add("client_id", kakaoClientId);
        form.add("redirect_uri", redirectUri(KAKAO)); form.add("code", code);
        if (StringUtils.hasText(kakaoClientSecret)) form.add("client_secret", kakaoClientSecret);
        Map<String, Object> token = postForm("https://kauth.kakao.com/oauth/token", form);
        String access = token == null ? null : str(token.get("access_token"));
        if (!StringUtils.hasText(access)) throw new IllegalArgumentException("카카오 인증에 실패했어요. 다시 시도해 주세요.");
        Map<String, Object> me = getJson("https://kapi.kakao.com/v2/user/me", access);
        if (me == null || me.get("id") == null) throw new IllegalArgumentException("카카오 사용자 정보를 가져오지 못했어요.");
        Map<String, Object> acct = (Map<String, Object>) me.getOrDefault("kakao_account", Map.of());
        Map<String, Object> prof = (Map<String, Object>) acct.getOrDefault("profile", Map.of());
        // 「확인됨」이라고 분명히 밝힌 이메일만 믿는다 — 예전엔 값이 빠져 있어도 믿었다
        boolean verified = Boolean.TRUE.equals(acct.get("is_email_verified")) && !Boolean.FALSE.equals(acct.get("is_email_valid"));
        return new SnsIdentity(KAKAO, str(me.get("id")), str(acct.get("email")), str(prof.get("nickname")), str(prof.get("profile_image_url")), verified);
    }

    @SuppressWarnings("unchecked")
    private SnsIdentity naver(String code, String state) {
        if (!StringUtils.hasText(naverClientId)) throw new IllegalArgumentException("네이버 로그인이 아직 준비되지 않았어요.");
        if (!StringUtils.hasText(code)) throw new IllegalArgumentException("네이버 인증 정보를 받지 못했어요.");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code"); form.add("client_id", naverClientId); form.add("client_secret", naverClientSecret);
        form.add("code", code); form.add("state", state == null ? "" : state);
        Map<String, Object> token = postForm("https://nid.naver.com/oauth2.0/token", form);
        String access = token == null ? null : str(token.get("access_token"));
        if (!StringUtils.hasText(access)) throw new IllegalArgumentException("네이버 인증에 실패했어요. 다시 시도해 주세요.");
        Map<String, Object> me = getJson("https://openapi.naver.com/v1/nid/me", access);
        Map<String, Object> r = me == null ? null : (Map<String, Object>) me.get("response");
        if (r == null || r.get("id") == null) throw new IllegalArgumentException("네이버 사용자 정보를 가져오지 못했어요.");
        String name = StringUtils.hasText(str(r.get("name"))) ? str(r.get("name")) : str(r.get("nickname"));
        // 네이버는 이메일 확인 여부를 알려 주지 않는다 — 미리 채우기에만 쓰고 계정 찾기·아이디로는 쓰지 않는다
        return new SnsIdentity(NAVER, str(r.get("id")), str(r.get("email")), name, str(r.get("profile_image")), false);
    }

    /**
     * 애플 — id_token(JWT)을 애플 공개키로 검증한다. iss·aud·만료를 본다.
     * 이메일은 첫 로그인 때만 claim 에 들어오고, 이름은 토큰에 아예 없어 프론트가 첫 응답의 user 를 넘긴다.
     */
    private SnsIdentity apple(String idToken, String userName) {
        if (!StringUtils.hasText(appleClientId)) throw new IllegalArgumentException("애플 로그인이 아직 준비되지 않았어요.");
        if (!StringUtils.hasText(idToken)) throw new IllegalArgumentException("애플 인증 정보를 받지 못했어요.");
        try {
            Locator<Key> locator = header -> {
                String kid = ((ProtectedHeader) header).getKeyId();
                for (Jwk<?> k : appleKeys(false).getKeys()) if (kid != null && kid.equals(k.getId())) return k.toKey();
                for (Jwk<?> k : appleKeys(true).getKeys())  if (kid != null && kid.equals(k.getId())) return k.toKey();
                throw new IllegalArgumentException("애플 서명 키를 찾지 못했어요.");
            };
            Claims c = Jwts.parser().keyLocator(locator)
                    .requireIssuer("https://appleid.apple.com").requireAudience(appleClientId)
                    .build().parseSignedClaims(idToken).getPayload();
            String email = c.get("email", String.class);
            boolean verified = Boolean.TRUE.equals(toBool(c.get("email_verified")));
            return new SnsIdentity(APPLE, c.getSubject(), email, StringUtils.hasText(userName) ? userName.trim() : null, null, verified);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("애플 인증에 실패했어요. 다시 시도해 주세요.");
        }
    }

    private JwkSet appleKeys(boolean force) {
        long now = System.currentTimeMillis();
        if (force || appleKeys == null || now - appleKeysAt > 3_600_000L) {
            String json = rest.getForObject("https://appleid.apple.com/auth/keys", String.class);
            appleKeys = Jwks.setParser().build().parse(json);
            appleKeysAt = now;
        }
        return appleKeys;
    }

    /* ───────────── 가입 티켓 ───────────── */

    public String issueTicket(SnsIdentity id) {
        String ticket = UUID.randomUUID().toString().replace("-", "");
        try { redis.setRedis(REDIS_DB, TICKET_PREFIX + ticket, om.writeValueAsString(id), TICKET_TTL_SEC); }
        catch (Exception e) { throw new IllegalStateException("가입 정보를 보관하지 못했어요."); }
        return ticket;
    }

    /** 티켓을 읽고 지운다(1회용). 없거나 만료면 null. */
    public SnsIdentity consumeTicket(String ticket) {
        if (!StringUtils.hasText(ticket)) return null;
        Object raw = redis.getRedis(REDIS_DB, TICKET_PREFIX + ticket);
        if (raw == null) return null;
        redis.delRedis(REDIS_DB, TICKET_PREFIX + ticket);
        try { return om.readValue(String.valueOf(raw), SnsIdentity.class); } catch (Exception e) { return null; }
    }

    /* ───────────── 유틸 ───────────── */

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String url, String bearer) {
        HttpHeaders h = new HttpHeaders();
        if (bearer != null) h.setBearerAuth(bearer);
        try {
            ResponseEntity<Map> r = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Map.class);
            return r.getBody();
        } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(String url, MultiValueMap<String, String> form) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<Map> r = rest.postForEntity(url, new HttpEntity<>(form, h), Map.class);
            return r.getBody();
        } catch (Exception e) { return null; }
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Boolean toBool(Object o) { return o == null ? null : o instanceof Boolean b ? b : Boolean.valueOf(String.valueOf(o)); }
}
