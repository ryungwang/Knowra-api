package com.knowra.share;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

/**
 * 검색엔진용 사이트맵 — 공개된 커뮤니티와 그 글. 프론트와 같은 도메인에서 nginx 가 /api 를 넘겨 주므로
 * 주소는 /api/sitemap.xml, 그 안의 URL 은 프론트 주소(knowra.front.base-url)다. robots.txt 가 이 주소를 가리킨다.
 *
 * <p>비공개 커뮤니티와 그 글은 넣지 않는다(멤버만 볼 수 있다). 사용자 프로필도 넣지 않는다 — 사람을 검색에
 * 올리는 건 본인이 고를 일이다. 글은 최근 것부터 45,000개(프로토콜 상한 50,000 밑).
 * 만드는 데 표 두 개를 훑으므로 한 시간 들고 있는다.
 */
@RestController
@RequiredArgsConstructor
public class SitemapController {

    private static final int MAX_POSTS = 45_000;
    private static final Duration TTL = Duration.ofHours(1);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JdbcTemplate jdbc;

    @Value("${knowra.front.base-url:http://localhost:5111}")
    private String frontBase;

    private volatile String cached;
    private volatile long cachedAt;

    @GetMapping(value = "/api/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        if (cached == null || System.currentTimeMillis() - cachedAt > TTL.toMillis()) {
            cached = build();
            cachedAt = System.currentTimeMillis();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(TTL).cachePublic()).body(cached);
    }

    private String build() {
        String front = frontBase.replaceAll("/+$", "");
        StringBuilder sb = new StringBuilder(1 << 16);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(sb, front + "/", null, "daily");
        url(sb, front + "/communities", null, "daily");
        jdbc.query("""
                SELECT c.comm_nm, GREATEST(c.frst_crt_dt, MAX(p.frst_crt_dt)) AS lastmod
                FROM knowra_community.tbl_comm c
                LEFT JOIN knowra_post.tbl_post p ON p.comm_sn = c.comm_sn AND p.actvtn_yn = 'Y' AND p.stat = 'ACTIVE'
                WHERE c.stat = 'Y' AND c.prvcy_stng <> 'private'
                GROUP BY c.comm_sn, c.comm_nm, c.frst_crt_dt ORDER BY c.comm_sn""",
                rs -> { url(sb, front + "/community/" + enc(rs.getString(1)), rs.getTimestamp(2), "daily"); });
        jdbc.query("""
                SELECT p.post_sn, c.comm_nm, COALESCE(p.mdfcn_dt, p.frst_crt_dt)
                FROM knowra_post.tbl_post p LEFT JOIN knowra_community.tbl_comm c ON c.comm_sn = p.comm_sn
                WHERE p.actvtn_yn = 'Y' AND p.stat = 'ACTIVE'
                  AND (p.comm_sn IS NULL OR (c.stat = 'Y' AND c.prvcy_stng <> 'private'))
                ORDER BY p.post_sn DESC LIMIT ?""",
                rs -> {
                    String comm = rs.getString(2);
                    String path = comm == null ? "/posts/" + rs.getLong(1) : "/community/" + enc(comm) + "/posts/" + rs.getLong(1);
                    url(sb, front + path, rs.getTimestamp(3), "weekly");
                }, MAX_POSTS);
        return sb.append("</urlset>\n").toString();
    }

    private static void url(StringBuilder sb, String loc, Timestamp lastmod, String freq) {
        sb.append("  <url><loc>").append(xml(loc)).append("</loc>");
        if (lastmod != null) sb.append("<lastmod>").append(lastmod.toLocalDateTime().format(DAY)).append("</lastmod>");
        sb.append("<changefreq>").append(freq).append("</changefreq></url>\n");
    }

    private static String enc(String s) { return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"); }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
