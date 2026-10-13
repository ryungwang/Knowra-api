package com.knowra.share;

import com.knowra.community.entity.TblComm;
import com.knowra.community.repository.TblCommRepository;
import com.knowra.post.entity.PostDTO;
import com.knowra.post.service.PostQuery;
import com.querydsl.core.Tuple;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.knowra.post.service.PostQuery.POST;

/**
 * 공유 링크 — 카카오톡·슬랙·X 등의 링크 미리보기용.
 *
 * <p>프론트는 SPA 라 index.html 하나뿐이고, 미리보기 봇은 JS 를 돌리지 않는다. 그래서 공유 주소는
 * 이 서버의 {@code /s/...} 로 만들고, 여기서 <b>OG 태그가 박힌 작은 HTML</b>을 준다. 사람이 열면
 * meta refresh + location.replace 로 즉시 프론트 주소로 넘어간다. UA 를 가려 보내지 않는 이유:
 * 봇 목록은 끝이 없고, 틀리면 미리보기가 조용히 깨진다 — 모두에게 같은 HTML 이 더 단단하다.
 *
 * <p><b>비로그인 기준으로만 보여 준다</b>({@code visibleTo(null)}). 비공개 커뮤니티 글의 제목이
 * 미리보기로 새면 안 된다 — 그런 글은 "로그인해서 보기" 일반 카드로 대신한다.
 */
@RestController
@RequiredArgsConstructor
public class ShareController {

    private static final Pattern IMG = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private final PostQuery postQuery;
    private final TblCommRepository commRepository;

    @Value("${knowra.front.base-url:http://localhost:5111}")
    private String frontBase;

    @GetMapping("/s/p/{postSn}")
    public ResponseEntity<String> post(@PathVariable long postSn, HttpServletRequest req) {
        String target = front() + "/posts/" + postSn;
        Tuple t = postQuery.base().where(POST.postSn.eq(postSn), PostQuery.alive(), PostQuery.visibleTo(null)).fetchOne();
        if (t == null) return page(target, "knowra", "로그인하면 볼 수 있는 글이거나 삭제된 글이에요.", null, "website", req);

        PostDTO p = postQuery.toDto(t);
        String plain = plain(p.getPostCntnt());
        String where = p.getCommDsplNm() != null ? p.getCommDsplNm() : (p.getCommNm() != null ? p.getCommNm() : null);
        String desc = (where != null ? where + " · " : "") + (p.getAuthorNm() != null ? p.getAuthorNm() : p.getNickName())
                + (plain.isEmpty() ? "" : " — " + cut(plain, 120));
        String img = firstImage(p.getPostCntnt(), req);
        return page(target, p.getPostTtl(), desc, img, "article", req);
    }

    @GetMapping("/s/c/{commNm}")
    public ResponseEntity<String> community(@PathVariable String commNm, HttpServletRequest req) {
        String target = front() + "/community/" + commNm;
        TblComm c = commRepository.findByCommNm(commNm);
        if (c == null || !"Y".equals(c.getStat())) return page(target, "knowra", "커뮤니티를 찾을 수 없어요.", null, "website", req);
        String name = c.getCommDsplNm() != null ? c.getCommDsplNm() : c.getCommNm();
        // 비공개 커뮤니티는 이름만 — 소개글은 멤버에게만 보이는 정보로 취급한다.
        String desc = "private".equals(c.getPrvcyStng()) ? "비공개 커뮤니티예요. 가입하면 볼 수 있어요."
                : (c.getCommDesc() == null || c.getCommDesc().isBlank() ? "knowra 커뮤니티 · 멤버 " + c.getMemberCnt() + "명" : cut(plain(c.getCommDesc()), 140));
        String img = c.getLogoFile() == null ? null
                : api(req) + c.getLogoFile().getAtchFilePathNm() + "/" + c.getLogoFile().getStrgFileNm() + "." + c.getLogoFile().getAtchFileExtnNm();
        return page(target, name, desc, img, "website", req);
    }

    /* ───────── HTML ───────── */

    private ResponseEntity<String> page(String target, String title, String desc, String image, String type, HttpServletRequest req) {
        String img = image != null ? image : front() + "/og-default.png";
        boolean large = image != null;   // 본문 이미지가 있을 때만 큰 카드 — 기본 이미지는 작게
        String t = esc(title), d = esc(desc), u = esc(target), i = esc(img);
        String html = "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">"
                + "<title>" + t + " · knowra</title>"
                + "<meta name=\"description\" content=\"" + d + "\">"
                + "<link rel=\"canonical\" href=\"" + u + "\">"
                + "<meta property=\"og:site_name\" content=\"knowra\">"
                + "<meta property=\"og:type\" content=\"" + type + "\">"
                + "<meta property=\"og:locale\" content=\"ko_KR\">"
                + "<meta property=\"og:title\" content=\"" + t + "\">"
                + "<meta property=\"og:description\" content=\"" + d + "\">"
                + "<meta property=\"og:url\" content=\"" + u + "\">"
                + "<meta property=\"og:image\" content=\"" + i + "\">"
                + "<meta name=\"twitter:card\" content=\"" + (large ? "summary_large_image" : "summary") + "\">"
                + "<meta name=\"twitter:title\" content=\"" + t + "\">"
                + "<meta name=\"twitter:description\" content=\"" + d + "\">"
                + "<meta name=\"twitter:image\" content=\"" + i + "\">"
                + "<meta http-equiv=\"refresh\" content=\"0;url=" + u + "\">"
                + "<script>location.replace(" + jsStr(target) + ")</script>"
                + "</head><body><a href=\"" + u + "\">" + t + "</a></body></html>";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(html);
    }

    private String front() { return frontBase.replaceAll("/+$", ""); }

    private static String api(HttpServletRequest req) {
        return ServletUriComponentsBuilder.fromContextPath(req).build().toUriString().replaceAll("/+$", "");
    }

    /** 본문 첫 이미지. 업로드 경로(/upload/...)는 API 주소로 절대화한다 — 봇은 상대 경로를 못 따라간다. */
    private static String firstImage(String html, HttpServletRequest req) {
        if (html == null) return null;
        Matcher m = IMG.matcher(html);
        if (!m.find()) return null;
        String src = m.group(1);
        if (src.startsWith("http://") || src.startsWith("https://")) return src;
        if (src.startsWith("/")) return api(req) + src;
        return null;   // data: 등은 미리보기에 못 쓴다
    }

    private static String plain(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    private static String cut(String s, int n) { return s.length() > n ? s.substring(0, n) + "…" : s; }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** script 안에 넣는 JS 문자열 — </script> 탈출까지 막는다. */
    private static String jsStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace(">", "\\u003e") + "\"";
    }
}
