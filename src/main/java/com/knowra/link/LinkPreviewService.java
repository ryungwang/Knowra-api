package com.knowra.link;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowra.cmm.service.RedisApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 링크 미리보기 — 글 본문의 바깥 링크를 OG 카드로. 결과(실패 포함)는 Redis 에 24시간 둔다:
 * 같은 링크를 열람자마다 다시 긁지 않게, 그리고 실패한 주소를 계속 두드리지 않게.
 * 미리보기 이미지는 바깥 주소 그대로 내보내지 않고 우리 프록시 경로로 바꾼다(열람자 IP 가 남의
 * 서버에 찍히지 않게) — {@link LinkController#image}.
 */
@Service
@RequiredArgsConstructor
public class LinkPreviewService {

    private static final int REDIS_DB = 15;
    private static final int TTL = 24 * 3600;
    private static final int MAX_HTML = 512 * 1024;
    private final RedisApiService redis;
    private final ObjectMapper om = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public Map<String, Object> preview(String url) {
        String key = "link:og:" + Integer.toHexString(url.hashCode()) + ":" + url.length();
        Object cached = redis.getRedis(REDIS_DB, key);
        if (cached != null) {
            try {
                Map<String, Object> m = om.readValue(String.valueOf(cached), Map.class);
                if (url.equals(m.get("url"))) return m.isEmpty() || Boolean.TRUE.equals(m.get("failed")) ? null : m;
            } catch (Exception ignore) { }
        }
        Map<String, Object> out = fetch(url);
        try {
            Map<String, Object> toCache = out != null ? out : Map.of("url", url, "failed", true);
            redis.setRedis(REDIS_DB, key, om.writeValueAsString(toCache), TTL);
        } catch (Exception ignore) { }
        return out;
    }

    private Map<String, Object> fetch(String url) {
        try {
            SafeFetcher.Result r = SafeFetcher.get(url, "text/html,application/xhtml+xml", MAX_HTML);
            if (r.status() < 200 || r.status() >= 300 || !r.contentType().toLowerCase().contains("html")) return null;
            String html = new String(r.body(), charsetOf(r.contentType(), r.body()));
            return parse(html, r.finalUrl(), url);
        } catch (Exception e) {
            return null;
        }
    }

    static Charset charsetOf(String contentType, byte[] body) {
        Matcher m = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
        String cs = m.find() ? m.group(1) : null;
        if (cs == null) {
            String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
            Matcher mm = Pattern.compile("<meta[^>]+charset=[\"']?([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(head);
            if (mm.find()) cs = mm.group(1);
        }
        try { return cs == null ? StandardCharsets.UTF_8 : Charset.forName(cs); } catch (Exception e) { return StandardCharsets.UTF_8; }
    }

    /** OG → 트위터 카드 → title/description 순으로 채운다. 이미지는 절대 주소로. */
    static Map<String, Object> parse(String html, String baseUrl, String originalUrl) {
        String head = html.length() > 200_000 ? html.substring(0, 200_000) : html;
        String title = first(meta(head, "og:title"), meta(head, "twitter:title"), tag(head, "title"));
        if (title == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", originalUrl);
        m.put("title", clip(unescape(title), 120));
        String desc = first(meta(head, "og:description"), meta(head, "twitter:description"), meta(head, "description"));
        m.put("description", desc == null ? null : clip(unescape(desc), 200));
        String site = first(meta(head, "og:site_name"), null, null);
        String host = java.net.URI.create(baseUrl).getHost();
        m.put("siteName", site != null ? clip(unescape(site), 60) : (host == null ? null : host.replaceFirst("^www\\.", "")));
        String img = first(meta(head, "og:image"), meta(head, "og:image:url"), meta(head, "twitter:image"));
        if (img != null) {
            try { img = java.net.URI.create(baseUrl).resolve(unescape(img).trim()).toString(); } catch (Exception e) { img = null; }
            if (img != null && !img.startsWith("http")) img = null;
        }
        m.put("image", img);
        return m;
    }

    private static String meta(String html, String key) {
        // property/name 과 content 순서가 어느 쪽이든
        Pattern p1 = Pattern.compile("<meta[^>]+(?:property|name)=[\"']" + Pattern.quote(key) + "[\"'][^>]*content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
        Pattern p2 = Pattern.compile("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:property|name)=[\"']" + Pattern.quote(key) + "[\"']", Pattern.CASE_INSENSITIVE);
        Matcher m = p1.matcher(html);
        if (m.find() && !m.group(1).isBlank()) return m.group(1);
        m = p2.matcher(html);
        if (m.find() && !m.group(1).isBlank()) return m.group(1);
        return null;
    }

    private static String tag(String html, String name) {
        Matcher m = Pattern.compile("<" + name + "[^>]*>([^<]{1,500})</" + name + ">", Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String first(String a, String b, String c) { return a != null ? a : b != null ? b : c; }
    private static String clip(String s, int n) { s = s.replaceAll("\\s+", " ").trim(); return s.length() > n ? s.substring(0, n) + "…" : s; }

    static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#039;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
    }
}
