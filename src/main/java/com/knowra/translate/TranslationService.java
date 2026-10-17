package com.knowra.translate;

import com.knowra.cmm.i18n.LangDetect;
import com.knowra.post.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 글·목록 제목·댓글·대화 메시지를 읽는 사람 언어로. (대상, 언어)마다 한 번 번역해 두고 같이 쓴다.
 *
 * <p>돌려주는 status:
 * <ul>
 *   <li>ok — 번역본(title·body·from)</li>
 *   <li>same — 원문이 이미 그 언어다(번역할 것 없음)</li>
 *   <li>skip — 너무 짧거나 언어를 모름(「ㅋㅋ」 같은 것)</li>
 *   <li>unavailable — 번역 업체가 꺼져 있거나 이달 한도를 다 썼거나 업체가 실패했다 → 화면은 원문(+브라우저 번역 단추)</li>
 * </ul>
 * 볼 수 있는 글·댓글만 번역한다(비공개 커뮤니티·차단·삭제는 상세·목록과 같은 기준) — 번역 창구로 못 볼 글을 읽게 되면 안 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    static final int MAX_CMTS = 100, MAX_TITLES = 50;

    private final JdbcTemplate jdbc;
    private final ObjectProvider<Translator> translator;
    private final TranslateQuota quota;
    private final PostService postService;

    public boolean available() { return translator.getIfAvailable() != null; }

    public Map<String, Object> post(long postSn, Long me, String to) {
        if (!postService.canSeePost(postSn, me)) throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        Map<String, Object> p = jdbc.queryForMap("SELECT post_ttl, post_cntnt, lang FROM knowra_post.tbl_post WHERE post_sn = ?", postSn);
        String title = (String) p.get("post_ttl"), body = (String) p.get("post_cntnt");
        String script = p.get("lang") != null ? (String) p.get("lang") : LangDetect.detect(title, body);
        if (!needs(script, to)) return status(unknown(script) ? "skip" : "same");
        String hash = sha1("POST", title, body);
        Map<String, Object> hit = cached("POST", postSn, to, hash);
        if (hit != null) return hit;
        List<Translator.Out> out = call(List.of(TranslateMarkup.titleOut(title), TranslateMarkup.htmlOut(body)), to);
        if (out == null) return status("unavailable");
        String from = out.get(1).from() != null ? out.get(1).from() : out.get(0).from();
        learnLang(postSn, script, from);
        if (sameLang(from, to)) return save("POST", postSn, to, hash, from, true, null, null);
        return save("POST", postSn, to, hash, from, false, TranslateMarkup.titleBack(out.get(0).html()), TranslateMarkup.htmlBack(out.get(1).html()));
    }

    /**
     * 목록 카드의 제목 여럿 — 결과는 글 번호 → {status, title, from}. 볼 수 있는 글만.
     * 본문까지 번역해 둔 글은 그 제목을 쓰고, 아니면 제목만 따로 번역해 둔다(TITLE) — 목록은 본문을 번역하지 않는다(한도를 아낀다).
     * 제목은 짧아 언어 판별이 덜 정확하므로 글의 원문 언어로 적지 않는다(learnLang 은 본문 번역에서만).
     */
    public Map<Long, Map<String, Object>> titles(List<Long> postSns, Long me, String to) {
        List<Long> want = postSns.stream().distinct().limit(MAX_TITLES).toList();
        Map<Long, Map<String, Object>> res = new LinkedHashMap<>();
        java.util.Set<Long> visible = postService.visiblePostSns(want, me);
        if (visible.isEmpty()) return res;
        String in = String.join(",", visible.stream().map(String::valueOf).toList());
        List<Long> sendSn = new ArrayList<>();
        List<String> sendText = new ArrayList<>(), sendHash = new ArrayList<>();
        jdbc.query("SELECT post_sn, post_ttl, post_cntnt, lang FROM knowra_post.tbl_post WHERE post_sn IN (" + in + ")", rs -> {
            long sn = rs.getLong(1);
            String title = rs.getString(2), body = rs.getString(3), lang = rs.getString(4);
            String script = lang != null ? lang : LangDetect.detect(title, body);
            if (title == null || title.isBlank() || !needs(script, to)) { res.put(sn, status(unknown(script) || title == null || title.isBlank() ? "skip" : "same")); return; }
            Map<String, Object> full = cached("POST", sn, to, sha1("POST", title, body));
            if (full != null) { res.put(sn, titleOnly(full)); return; }
            String hash = sha1("TITLE", title, null);
            Map<String, Object> hit = cached("TITLE", sn, to, hash);
            if (hit != null) { res.put(sn, titleOnly(hit)); return; }
            sendSn.add(sn); sendText.add(TranslateMarkup.titleOut(title)); sendHash.add(hash);
        });
        if (sendSn.isEmpty()) return res;
        List<Translator.Out> out = call(sendText, to);
        for (int i = 0; i < sendSn.size(); i++) {
            if (out == null) { res.put(sendSn.get(i), status("unavailable")); continue; }
            Translator.Out o = out.get(i);
            res.put(sendSn.get(i), titleOnly(sameLang(o.from(), to)
                    ? save("TITLE", sendSn.get(i), to, sendHash.get(i), o.from(), true, null, null)
                    : save("TITLE", sendSn.get(i), to, sendHash.get(i), o.from(), false, TranslateMarkup.titleBack(o.html()), null)));
        }
        return res;
    }

    private static Map<String, Object> titleOnly(Map<String, Object> m) {
        if (!"ok".equals(m.get("status"))) return m;
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("status", "ok");
        t.put("from", m.get("from"));
        t.put("title", m.get("title"));
        return t;
    }

    /** 한 글의 댓글 여러 개 — 결과는 댓글 번호 → {status, body, from} */
    public Map<Long, Map<String, Object>> comments(long postSn, List<Long> cmtSns, Long me, String to) {
        if (!postService.canSeePost(postSn, me)) throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        List<Long> want = cmtSns.stream().distinct().limit(MAX_CMTS).toList();
        Map<Long, Map<String, Object>> res = new LinkedHashMap<>();
        if (want.isEmpty()) return res;
        String in = String.join(",", want.stream().map(String::valueOf).toList());
        // 목록과 같은 기준 — 지운·가린 댓글, 차단 관계(양방향)의 댓글은 번역해 주지 않는다
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.post_cmt_sn, c.cmt_cntnt FROM knowra_post.tbl_post_cmt c
                WHERE c.post_sn = ? AND c.post_cmt_sn IN (""" + in + """
                ) AND c.actvtn_yn = 'Y' AND c.stat = 'ACTIVE'
                  AND (CAST(? AS BIGINT) IS NULL OR NOT EXISTS (SELECT 1 FROM knowra_user.tbl_user_block b
                       WHERE (b.user_sn = ? AND b.blocked_sn = c.user_sn) OR (b.user_sn = c.user_sn AND b.blocked_sn = ?)))""",
                postSn, me, me, me);
        Map<Long, String> texts = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) texts.put(((Number) r.get("post_cmt_sn")).longValue(), (String) r.get("cmt_cntnt"));
        return plainMany("CMT", texts, to, res);
    }

    /**
     * 대화방 메시지 여러 개 — 그 방의 멤버만, 남이 보낸 것만(내 메시지는 내 언어다). 지운·가린 메시지는 번역하지 않는다.
     * 결과는 메시지 번호 → {status, body, from}
     */
    public Map<Long, Map<String, Object>> messages(long roomSn, List<Long> msgSns, Long me, String to) {
        Integer member = jdbc.queryForObject("SELECT COUNT(*) FROM knowra_user.tbl_chat_room_mbr WHERE room_sn = ? AND user_sn = ? AND stat IN ('ACTIVE', 'REQUEST')",
                Integer.class, roomSn, me);
        if (member == null || member == 0) throw new IllegalArgumentException("대화방을 찾을 수 없어요.");
        List<Long> want = msgSns.stream().distinct().limit(MAX_CMTS).toList();
        Map<Long, Map<String, Object>> res = new LinkedHashMap<>();
        if (want.isEmpty()) return res;
        String in = String.join(",", want.stream().map(String::valueOf).toList());
        Map<Long, String> texts = new LinkedHashMap<>();
        jdbc.query("""
                SELECT msg_sn, msg_cntnt FROM knowra_user.tbl_chat_msg
                WHERE room_sn = ? AND msg_sn IN (""" + in + """
                ) AND user_sn <> ? AND hidden_at IS NULL AND deleted_at IS NULL AND COALESCE(msg_cntnt, '') <> ''""",
                rs -> { texts.put(rs.getLong(1), rs.getString(2)); }, roomSn, me);
        return plainMany("MSG", texts, to, res);
    }

    /** 일반 글자(댓글·메시지) 여럿을 한 번에 — 캐시에 있는 건 그대로, 나머지만 업체에 */
    private Map<Long, Map<String, Object>> plainMany(String kind, Map<Long, String> texts, String to, Map<Long, Map<String, Object>> res) {
        List<Long> sendSn = new ArrayList<>();
        List<String> sendText = new ArrayList<>(), sendHash = new ArrayList<>();
        texts.forEach((sn, text) -> {
            String script = LangDetect.detect(null, text);
            if (!needs(script, to)) { res.put(sn, status(unknown(script) ? "skip" : "same")); return; }
            String hash = sha1(kind, null, text);
            Map<String, Object> hit = cached(kind, sn, to, hash);
            if (hit != null) { res.put(sn, hit); return; }
            sendSn.add(sn); sendText.add(TranslateMarkup.plainOut(text)); sendHash.add(hash);
        });
        if (sendSn.isEmpty()) return res;
        List<Translator.Out> out = call(sendText, to);
        for (int i = 0; i < sendSn.size(); i++) {
            if (out == null) { res.put(sendSn.get(i), status("unavailable")); continue; }
            Translator.Out o = out.get(i);
            res.put(sendSn.get(i), sameLang(o.from(), to)
                    ? save(kind, sendSn.get(i), to, sendHash.get(i), o.from(), true, null, null)
                    : save(kind, sendSn.get(i), to, sendHash.get(i), o.from(), false, null, TranslateMarkup.plainBack(o.html())));
        }
        return res;
    }

    /**
     * 원문 언어로 보아 번역이 필요할 수 있나 — 확실히 같은 언어면 업체에 묻지 않는다.
     * script 는 글자 체계(ko·ja·zh·latn, LangDetect) 또는 업체가 알려 준 정확한 언어(en·es·zh-Hans …, learnLang).
     */
    static boolean needs(String script, String to) {
        if (unknown(script)) return false;
        if (script.equals("latn") || script.equals("zh")) return true;   // 영어인지 스페인어인지, 간체인지 번체인지 업체가 가린다
        return !sameLang(script, to);
    }

    private static boolean unknown(String script) { return script == null || script.equals("und"); }

    /**
     * 업체가 판별한 원문 언어를 글에 적어 둔다 — 글자 체계(latn·zh)만 알던 글이 en·es·zh-Hans 처럼 정확해져,
     * 다음부터 같은 언어 독자에게는 업체에 묻지 않는다. 글을 고치면 글자 체계로 다시 돌아간다(PostService).
     */
    private void learnLang(long postSn, String script, String from) {
        if (from == null || !("latn".equals(script) || "zh".equals(script))) return;
        String lang = from.length() > 10 ? from.substring(0, 10) : from;
        jdbc.update("UPDATE knowra_post.tbl_post SET lang = ? WHERE post_sn = ? AND lang IN ('latn', 'zh')", lang, postSn);
    }

    /** 업체가 판별한 원문 언어가 읽는 사람 언어와 같은가 — zh 는 간체·번체까지 같아야 같다 */
    static boolean sameLang(String from, String to) {
        if (from == null) return false;
        if (from.startsWith("zh") || to.startsWith("zh")) return from.equalsIgnoreCase(to);
        return from.split("-")[0].equalsIgnoreCase(to.split("-")[0]);
    }

    /** 한도 안에서 업체를 부른다. 꺼져 있거나 한도를 넘거나 실패하면 null */
    private List<Translator.Out> call(List<String> html, String to) {
        Translator t = translator.getIfAvailable();
        if (t == null) return null;
        long chars = html.stream().mapToLong(String::length).sum();
        if (!quota.reserve(chars)) { log.info("번역 이달 한도({}자) 도달 — 원문으로", quota.limit()); return null; }
        try {
            return t.translate(html, to);
        } catch (Exception e) {
            log.warn("번역 실패 — 원문으로: {}", e.toString());
            return null;
        }
    }

    private Map<String, Object> cached(String kind, long sn, String to, String hash) {
        List<Map<String, Object>> r = jdbc.queryForList("SELECT src_hash, src_lang, same, title, body FROM knowra_com.tbl_translation WHERE target_kind = ? AND target_sn = ? AND lang = ?", kind, sn, to);
        if (r.isEmpty() || !hash.equals(r.getFirst().get("src_hash"))) return null;   // 원문이 바뀌었으면 다시
        Map<String, Object> m = r.getFirst();
        return Boolean.TRUE.equals(m.get("same")) ? status("same") : ok((String) m.get("src_lang"), (String) m.get("title"), (String) m.get("body"));
    }

    private Map<String, Object> save(String kind, long sn, String to, String hash, String from, boolean same, String title, String body) {
        jdbc.update("""
                INSERT INTO knowra_com.tbl_translation (target_kind, target_sn, lang, src_hash, src_lang, same, title, body, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (target_kind, target_sn, lang) DO UPDATE SET src_hash = EXCLUDED.src_hash, src_lang = EXCLUDED.src_lang,
                    same = EXCLUDED.same, title = EXCLUDED.title, body = EXCLUDED.body, created_at = now()""",
                kind, sn, to, hash, from, same, title, body);
        return same ? status("same") : ok(from, title, body);
    }

    /** 지운 글·댓글·메시지의 번역본도 지운다 — 원문이 사라졌는데 사본이 남으면 안 된다 */
    @Scheduled(cron = "0 17 4 * * *", zone = "UTC")
    public void purge() {
        jdbc.update("""
                DELETE FROM knowra_com.tbl_translation t WHERE t.target_kind IN ('POST', 'TITLE')
                AND NOT EXISTS (SELECT 1 FROM knowra_post.tbl_post p WHERE p.post_sn = t.target_sn AND p.actvtn_yn = 'Y')""");
        jdbc.update("""
                DELETE FROM knowra_com.tbl_translation t WHERE t.target_kind = 'CMT'
                AND NOT EXISTS (SELECT 1 FROM knowra_post.tbl_post_cmt c WHERE c.post_cmt_sn = t.target_sn AND c.actvtn_yn = 'Y')""");
        jdbc.update("""
                DELETE FROM knowra_com.tbl_translation t WHERE t.target_kind = 'MSG'
                AND NOT EXISTS (SELECT 1 FROM knowra_user.tbl_chat_msg m WHERE m.msg_sn = t.target_sn AND m.hidden_at IS NULL AND m.deleted_at IS NULL)""");
    }

    private static Map<String, Object> status(String s) { return Map.of("status", s); }

    private static Map<String, Object> ok(String from, String title, String body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "ok");
        m.put("from", from);
        if (title != null) m.put("title", title);
        m.put("body", body);
        return m;
    }

    private static String sha1(String kind, String title, String body) {
        try {
            String s = kind + "\u0000" + (title == null ? "" : title) + "\u0000" + (body == null ? "" : body);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
