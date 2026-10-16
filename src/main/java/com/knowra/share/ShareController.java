package com.knowra.share;

import com.knowra.cmm.i18n.Lang;
import com.knowra.cmm.i18n.Msg;
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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.knowra.user.repository.TblUserRepository userRepository;

    @Value("${knowra.front.base-url:http://localhost:5111}")
    private String frontBase;

    @GetMapping("/s/p/{postSn}")
    public ResponseEntity<String> post(@PathVariable long postSn, HttpServletRequest req) {
        String target = front() + "/posts/" + postSn;
        Tuple t = postQuery.base().where(POST.postSn.eq(postSn), PostQuery.alive(), PostQuery.visibleTo(null)).fetchOne();
        if (t == null) return page(target, "knowra", Msg.tl(lang(req), "로그인하면 볼 수 있는 글이거나 삭제된 글이에요."), null, "website", req);

        PostDTO p = postQuery.toDto(t);
        // 스포일러는 미리보기 카드에도 새면 안 된다 — 글 전체면 본문·사진을 빼고, ||부분||은 가린 채로
        String plain = p.isSpoiler() ? Msg.tl(lang(req), "스포일러가 포함된 글이에요.") : hideSpoilers(plain(p.getPostCntnt()));
        String where = p.getCommDsplNm() != null ? p.getCommDsplNm() : (p.getCommNm() != null ? p.getCommNm() : null);
        if (postQuery.isAnonymousComm(p.getCommSn())) com.knowra.post.service.PostQuery.anonymize(p);   // 미리보기 카드도 익명
        String desc = (where != null ? where + " · " : "") + (p.getAuthorNm() != null ? p.getAuthorNm() : p.getNickName())
                + (plain.isEmpty() ? "" : " — " + cut(plain, 120));
        String img = p.isSpoiler() ? null : firstImage(p.getPostCntnt(), req);
        return page(target, p.getPostTtl(), desc, img, "article", req);
    }

    @GetMapping("/s/c/{commNm}")
    public ResponseEntity<String> community(@PathVariable String commNm, HttpServletRequest req) {
        String target = front() + "/community/" + commNm;
        TblComm c = commRepository.findByCommNm(commNm);
        if (c == null || !"Y".equals(c.getStat())) return page(target, "knowra", Msg.tl(lang(req), "커뮤니티를 찾을 수 없어요."), null, "website", req);
        String name = c.getCommDsplNm() != null ? c.getCommDsplNm() : c.getCommNm();
        // 비공개 커뮤니티는 이름만 — 소개글은 멤버에게만 보이는 정보로 취급한다.
        String lang = lang(req);
        String desc = "private".equals(c.getPrvcyStng()) ? Msg.tl(lang, "비공개 커뮤니티예요. 가입하면 볼 수 있어요.")
                : (c.getCommDesc() == null || c.getCommDesc().isBlank() ? Msg.tl(lang, "knowra 커뮤니티 · 멤버 {0}명", c.getMemberCnt()) : cut(plain(c.getCommDesc()), 140));
        String img = c.getLogoFile() == null ? null
                : absolute(c.getLogoFile().getUrl(), req);
        return page(target, name, desc, img, "website", req);
    }

    /** 초대 링크 — 「○○에 초대받았어요」 카드. 만료·취소된 링크도 일단 받는 화면으로 보낸다(거기서 이유를 알려 준다). */
    @GetMapping("/s/i/{code}")
    public ResponseEntity<String> invite(@PathVariable String code, HttpServletRequest req) {
        if (!code.matches("[A-Za-z0-9]{6,16}")) return page(front(), "knowra", Msg.tl(lang(req), "없는 초대 링크예요."), null, "website", req);
        String target = front() + "/invite/" + code;
        java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COMM_SN, CREATED_BY FROM knowra_community.tbl_comm_invite WHERE INVITE_CODE = ?", code);
        TblComm c = rows.isEmpty() ? null : commRepository.findByCommSn(((Number) rows.get(0).get("comm_sn")).longValue());
        if (c == null || !"Y".equals(c.getStat())) return page(target, Msg.tl(lang(req), "knowra 초대"), Msg.tl(lang(req), "커뮤니티 초대 링크예요."), null, "website", req);
        String name = c.getCommDsplNm() != null ? c.getCommDsplNm() : c.getCommNm();
        String inviter = userRepository.findById(((Number) rows.get(0).get("created_by")).longValue())
                .map(u -> u.getNickName()).orElse(null);
        String lang = lang(req);
        String desc = (inviter != null ? Msg.tl(lang, "{0}님이 초대했어요", inviter) : Msg.tl(lang, "knowra 커뮤니티 초대"))
                + " · " + ("private".equals(c.getPrvcyStng()) ? Msg.tl(lang, "비공개 커뮤니티") : Msg.tl(lang, "멤버 {0}명", c.getMemberCnt()));
        String img = c.getLogoFile() == null ? null
                : absolute(c.getLogoFile().getUrl(), req);
        return page(target, Msg.tl(lang, "{0}에 초대받았어요", name), desc, img, "website", req);
    }

    /* ───────── HTML ───────── */

    private ResponseEntity<String> page(String target, String title, String desc, String image, String type, HttpServletRequest req) {
        String img = image != null ? image : front() + "/og-default.png";
        boolean large = image != null;   // 본문 이미지가 있을 때만 큰 카드 — 기본 이미지는 작게
        String t = esc(title), d = esc(desc), u = esc(target), i = esc(img);
        String lang = lang(req);
        String html = "<!doctype html><html lang=\"" + lang + "\"><head><meta charset=\"utf-8\">"
                + "<title>" + t + " · knowra</title>"
                + "<meta name=\"description\" content=\"" + d + "\">"
                + "<link rel=\"canonical\" href=\"" + u + "\">"
                + "<meta property=\"og:site_name\" content=\"knowra\">"
                + "<meta property=\"og:type\" content=\"" + type + "\">"
                + "<meta property=\"og:locale\" content=\"" + OG_LOCALE.getOrDefault(lang, "ko_KR") + "\">"
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
                .header("Vary", "Accept-Language")   // 언어마다 문구가 다르다 — 캐시가 섞이면 안 된다
                .body(html);
    }

    /** 저장소 URL(운영은 S3 절대 주소)은 그대로, 예전 /upload 상대 경로는 API 주소를 붙인다 — 봇은 상대 경로를 못 따라간다 */
    private static String absolute(String url, HttpServletRequest req) {
        if (url == null) return null;
        return url.startsWith("http://") || url.startsWith("https://") ? url : api(req) + url;
    }

    /**
     * 미리보기 문구의 언어. ⚠️ Accept-Language 가 <b>없으면 한국어</b>다(Lang.fromHeader 의 기본은 영어) —
     * 머리글 없이 오는 미리보기 봇이 많은데, 지금까지 한국어로 나가던 카드가 영어로 바뀌면 안 된다.
     */
    private static String lang(HttpServletRequest req) {
        String h = req.getHeader("Accept-Language");
        return h == null || h.isBlank() ? Lang.KO : Lang.of(req);
    }

    private static final java.util.Map<String, String> OG_LOCALE = java.util.Map.of(
            Lang.KO, "ko_KR", Lang.EN, "en_US", Lang.JA, "ja_JP", Lang.ZH_HANS, "zh_CN", Lang.ZH_HANT, "zh_TW");

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

    /** ||가린 부분|| 은 미리보기에서 ▒ 로 — 글자 수도 드러나지 않게 길이를 고정한다 */
    static String hideSpoilers(String s) { return s.replaceAll("\\|\\|.+?\\|\\|", "▒▒▒"); }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** script 안에 넣는 JS 문자열 — </script> 탈출까지 막는다. */
    private static String jsStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace(">", "\\u003e") + "\"";
    }
}
