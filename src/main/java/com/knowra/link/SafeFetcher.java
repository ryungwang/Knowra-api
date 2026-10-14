package com.knowra.link;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * 바깥 URL 을 서버가 대신 가져올 때 쓰는 유일한 통로 — 링크 미리보기·미리보기 이미지 프록시.
 *
 * <p>사용자가 준 URL 로 서버가 요청을 보내므로 <b>SSRF</b> 를 막아야 한다:
 * <ul>
 *   <li>http/https, 포트 80·443(또는 생략)만</li>
 *   <li>호스트가 가리키는 <b>모든</b> IP 가 공인 주소여야 한다(루프백·사설·링크로컬(169.254 = 클라우드
 *       메타데이터)·CGNAT·멀티캐스트·IPv6 ULA 거절)</li>
 *   <li>리다이렉트는 자동으로 따라가지 않고 한 단계마다 위 검사를 다시 한다(최대 3번)</li>
 *   <li>응답은 최대 {@code maxBytes} 까지만 읽고, 전체 5초</li>
 * </ul>
 * ⚠️ 남는 위험: 검사 시점과 연결 시점 사이에 DNS 가 바뀌는 리바인딩은 여기서 완전히 못 막는다.
 * 운영에선 앱 서버의 사설망 egress 를 방화벽에서도 막아 두는 게 맞다.
 */
public final class SafeFetcher {

    private SafeFetcher() {}

    public record Result(int status, String contentType, byte[] body, String finalUrl) {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final int BODY_DEADLINE_SEC = 8;
    private static final java.util.concurrent.ScheduledExecutorService CLOSER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "safe-fetch-closer"); t.setDaemon(true); return t; });

    public static Result get(String url, String accept, int maxBytes) throws Exception {
        String current = url;
        for (int hop = 0; hop <= 3; hop++) {
            URI u = check(current);
            HttpRequest req = HttpRequest.newBuilder(u)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (compatible; KnowraLinkPreview/1.0)")
                    .header("Accept", accept)
                    .header("Accept-Language", "ko,en;q=0.8")
                    .GET().build();
            HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int code = res.statusCode();
            if (code >= 300 && code < 400) {
                res.body().close();
                String loc = res.headers().firstValue("Location").orElse(null);
                if (loc == null) throw new IllegalArgumentException("리다이렉트 주소가 없다");
                current = u.resolve(loc).toString();
                continue;
            }
            byte[] body;
            // 본문 읽기 전체에 마감을 건다 — 요청 timeout 은 헤더까지만이라 1바이트씩 흘리는 서버가 스레드를 무한정
            // 붙잡았다. 마감이 되면 다른 스레드가 스트림을 닫아 막힌 read() 를 풀어 준다.
            try (InputStream in = res.body()) {
                var closer = CLOSER.schedule(() -> { try { in.close(); } catch (Exception ignored) { } }, BODY_DEADLINE_SEC, java.util.concurrent.TimeUnit.SECONDS);
                try { body = readLimited(in, maxBytes); }
                catch (java.io.IOException e) { throw new IllegalArgumentException("본문을 제때 받지 못했다"); }
                finally { closer.cancel(false); }
            }
            return new Result(code, res.headers().firstValue("Content-Type").orElse(""), body, u.toString());
        }
        throw new IllegalArgumentException("리다이렉트가 너무 많다");
    }

    private static byte[] readLimited(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            int room = max - out.size();
            if (room <= 0) break;
            out.write(buf, 0, Math.min(n, room));
        }
        return out.toByteArray();
    }

    /** 허용되는 URL 이면 URI, 아니면 IllegalArgumentException. 테스트에서 직접 부른다. */
    public static URI check(String url) throws Exception {
        URI u = new URI(url.trim());
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new IllegalArgumentException("http/https 만");
        if (u.getHost() == null || u.getUserInfo() != null) throw new IllegalArgumentException("호스트가 이상하다");
        int port = u.getPort();
        if (port != -1 && port != 80 && port != 443) throw new IllegalArgumentException("포트 80/443 만");
        InetAddress[] addrs = InetAddress.getAllByName(u.getHost());
        if (addrs.length == 0) throw new IllegalArgumentException("주소를 못 찾았다");
        for (InetAddress a : addrs) if (!isPublic(a)) throw new IllegalArgumentException("공인 주소가 아니다");
        return u;
    }

    static boolean isPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            if (b0 == 0 || b0 == 127) return false;
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return false;     // CGNAT 100.64/10
            if (b0 == 169 && b1 == 254) return false;                  // 링크로컬·클라우드 메타데이터
            if (b0 == 192 && b1 == 0 && (b[2] & 0xff) == 0) return false;
            if (b0 >= 224) return false;                               // 멀티캐스트·예약
        } else {
            if ((b[0] & 0xfe) == 0xfc) return false;                   // IPv6 ULA fc00::/7
            // 안에 IPv4 를 품은 주소는 그 v4 로 다시 본다 — 6to4(2002::/16), NAT64(64:ff9b::/96)
            if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x02) {
                try { return isPublic(InetAddress.getByAddress(new byte[]{b[2], b[3], b[4], b[5]})); } catch (UnknownHostException e) { return false; }
            }
            if ((b[0] & 0xff) == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b) {
                try { return isPublic(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]})); } catch (UnknownHostException e) { return false; }
            }
            if (a instanceof Inet6Address i6 && i6.isIPv4CompatibleAddress()) return false;
            // IPv4-mapped(::ffff:a.b.c.d) 는 안의 v4 로 다시 본다
            boolean mapped = true;
            for (int i = 0; i < 10; i++) if (b[i] != 0) { mapped = false; break; }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try { return isPublic(InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]})); }
                catch (UnknownHostException e) { return false; }
            }
        }
        return true;
    }
}
