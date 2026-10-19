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
 * 글·목록 제목·댓글·대화 메시지·커뮤니티 소개를 읽는 사람 언어로. (대상, 언어)마다 한 번 번역해 두고 같이 쓴다.
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

    static final int MAX_CMTS = 100, MAX_TITLES = 50, MAX_QUERY = 100;

    private final JdbcTemplate jdbc;
    private final ObjectProvider<Translator> translator;
    private final TranslateQuota quota;
    private final PostService postService;
    private final com.knowra.cmm.ratelimit.RateLimiter limiter;
    private final com.knowra.wiki.WikiService wiki;   // 커뮤니티 읽기 권한 — 모임 목록과 같은 기준

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
        List<Translator.Out> out = call(List.of(TranslateMarkup.titleOut(title), TranslateMarkup.htmlOut(body)), to, me);
        if (out == null) return status("unavailable");
        String from = out.get(1).from() != null ? out.get(1).from() : out.get(0).from();
        learnLang(postSn, script, from);
        if (sameLang(from, to)) return save("POST", postSn, to, hash, from, true, null, null);
        return save("POST", postSn, to, hash, from, false, TranslateMarkup.titleBack(out.get(0).html()), TranslateMarkup.htmlBack(out.get(1).html()));
    }

    /** 프로필 자기소개 — 프로필은 누구나 보므로 권한은 따로 없다. 결과 {status, body, from} */
    public Map<String, Object> bio(String nickName, Long me, String to) {
        List<Map<String, Object>> us = jdbc.queryForList("SELECT user_sn, bio FROM knowra_user.tbl_user WHERE nick_name = ? AND COALESCE(bio, '') <> ''", nickName);
        if (us.isEmpty()) return status("skip");
        long sn = ((Number) us.getFirst().get("user_sn")).longValue();
        Map<Long, Map<String, Object>> res = plainMany("BIO", Map.of(sn, (String) us.getFirst().get("bio")), to, me, new LinkedHashMap<>());
        return res.getOrDefault(sn, status("skip"));
    }

    /**
     * 커뮤니티 위키 페이지 제목·본문 — 위키를 읽을 수 있는 사람만(비공개 커뮤니티는 멤버). 본문은 글과 같은 HTML 이다.
     * 운영진이 고친 뒤에는 원문이 달라져 다시 번역한다.
     */
    public Map<String, Object> wikiPage(long commSn, String slug, Long me, String to) {
        com.knowra.community.entity.TblComm comm = wiki.requireReadable(commSn, me);
        List<Map<String, Object>> ps = jdbc.queryForList("SELECT page_sn, title, content FROM knowra_community.tbl_comm_page WHERE comm_sn = ? AND slug = ?", commSn, slug);
        if (ps.isEmpty()) throw new IllegalArgumentException("페이지를 찾을 수 없어요.");
        Map<String, Object> p = ps.getFirst();
        long pageSn = ((Number) p.get("page_sn")).longValue();
        String title = (String) p.get("title"), body = (String) p.get("content");
        // 운영진이 고른 주 언어가 있으면 그것, 여러 언어면 글자로
        String script = comm.getLang() != null ? comm.getLang() : LangDetect.detect(title, body);
        if (!needs(script, to)) return status(unknown(script) ? "skip" : "same");
        String hash = sha1("WIKI", title, body);
        Map<String, Object> hit = cached("WIKI", pageSn, to, hash);
        if (hit != null) return hit;
        List<Translator.Out> out = call(List.of(TranslateMarkup.titleOut(title), TranslateMarkup.htmlOut(body)), to, me);
        if (out == null) return status("unavailable");
        String from = out.get(1).from() != null ? out.get(1).from() : out.get(0).from();
        if (sameLang(from, to)) return save("WIKI", pageSn, to, hash, from, true, null, null);
        return save("WIKI", pageSn, to, hash, from, false, TranslateMarkup.titleBack(out.get(0).html()), TranslateMarkup.htmlBack(out.get(1).html()));
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
        List<Translator.Out> out = call(sendText, to, me);
        for (int i = 0; i < sendSn.size(); i++) {
            if (out == null) { res.put(sendSn.get(i), status("unavailable")); continue; }
            Translator.Out o = out.get(i);
            res.put(sendSn.get(i), titleOnly(sameLang(o.from(), to)
                    ? save("TITLE", sendSn.get(i), to, sendHash.get(i), o.from(), true, null, null)
                    : save("TITLE", sendSn.get(i), to, sendHash.get(i), o.from(), false, TranslateMarkup.titleBack(o.html()), null)));
        }
        return res;
    }

    /**
     * 다른 언어 글 검색 — 검색어를 사이트의 다른 언어로 옮긴 것들(원문 제외). 「등산」 → hiking, ハイキング, 登山 …
     * (검색어, 언어)마다 한 번 번역하고 기억한다. 로그인하지 않은 사람은 기억해 둔 것만(call). 너무 긴 검색어는 하지 않는다.
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)   // 검색은 읽기 전용 거래 안에서 부른다 — 기억은 따로 쓴다
    public List<String> searchQueries(String q, Long me) {
        String norm = q == null ? "" : q.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
        if (norm.isEmpty() || norm.length() > MAX_QUERY) return List.of();
        String script = LangDetect.detectQuery(norm);
        if (unknown(script)) return List.of();
        Map<String, String> known = new java.util.HashMap<>();
        jdbc.query("SELECT lang, text FROM knowra_com.tbl_query_translation WHERE q = ?", rs -> { known.put(rs.getString(1), rs.getString(2)); }, norm);
        List<String> out = new ArrayList<>();
        Boolean fresh = null;   // 새로 번역할 수 있나 — 한 사람이 검색어를 바꿔 가며 한도를 쓰지 못하게 시간당 100개
        for (String to : com.knowra.cmm.i18n.Lang.ALL) {
            if (!needs(script, to)) continue;
            String text = known.get(to);
            if (text == null) {
                if (fresh == null) fresh = me != null && limiter.tryHit("api:translate-q:u:" + me, 100, 3600);
                List<Translator.Out> r = fresh ? call(List.of(TranslateMarkup.titleOut(norm)), to, me) : null;
                if (r == null) continue;
                Translator.Out o = r.getFirst();
                text = sameLang(o.from(), to) ? "" : TranslateMarkup.titleBack(o.html()).trim().toLowerCase(java.util.Locale.ROOT);
                if (text.length() > 400) text = "";
                jdbc.update("""
                        INSERT INTO knowra_com.tbl_query_translation (q, lang, text) VALUES (?, ?, ?)
                        ON CONFLICT (q, lang) DO UPDATE SET text = EXCLUDED.text, created_at = now()""", norm, to, text);
            }
            if (!text.isEmpty() && !text.equals(norm) && !out.contains(text)) out.add(text);
        }
        return out;
    }

    private static Map<String, Object> titleOnly(Map<String, Object> m) {
        if (!"ok".equals(m.get("status"))) return m;
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("status", "ok");
        t.put("from", m.get("from"));
        t.put("title", m.get("title"));
        return t;
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 커뮤니티 이름·소개·규칙 — 다른 언어 커뮤니티에 들어가기 전에 무엇을 하는 곳인지, 어떤 규칙에 동의하는지 읽을 수 있게.
     * 결과 {status, from, name, desc, rules:[{title, body}]}. 셋을 한 번에 번역해 (커뮤니티, 언어)마다 기억한다(COMM).
     * 이름·소개는 탐색 화면에도 보이니 누구에게나, 규칙은 볼 수 있는 사람에게만(비공개 커뮤니티는 멤버만 — WikiService 와 같은 기준).
     */
    public Map<String, Object> community(long commSn, Long me, String to) {
        List<Map<String, Object>> cs = jdbc.queryForList("SELECT comm_dspl_nm, comm_nm, comm_desc, prvcy_stng, lang FROM knowra_community.tbl_comm WHERE comm_sn = ? AND stat = 'Y' AND actvtn_yn = 'Y'", commSn);
        if (cs.isEmpty()) throw new IllegalArgumentException("커뮤니티를 찾을 수 없어요.");
        Map<String, Object> c = cs.getFirst();
        String name = c.get("comm_dspl_nm") != null && !((String) c.get("comm_dspl_nm")).isBlank() ? (String) c.get("comm_dspl_nm") : (String) c.get("comm_nm");
        String desc = c.get("comm_desc") == null ? "" : (String) c.get("comm_desc");
        List<String[]> rules = jdbc.query("SELECT title, body FROM knowra_community.tbl_comm_rule WHERE comm_sn = ? ORDER BY ord", (rs, i) -> new String[]{rs.getString(1), rs.getString(2) == null ? "" : rs.getString(2)}, commSn);
        boolean rulesVisible = !"private".equals(c.get("prvcy_stng")) || (me != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE' AND actvtn_yn = 'Y')", Boolean.class, commSn, me)));

        // 원문 — 이름, 소개, 규칙 제목·설명 순서. 빈 칸은 업체에 보내지 않는다
        List<String> src = new ArrayList<>(List.of(name, desc));
        for (String[] r : rules) { src.add(r[0]); src.add(r[1]); }
        StringBuilder all = new StringBuilder();
        for (int i = 1; i < src.size(); i++) all.append(src.get(i)).append('\n');
        // 운영진이 고른 주 언어가 있으면 그것, 여러 언어(null)면 글자로 판별
        String script = c.get("lang") != null ? (String) c.get("lang") : LangDetect.detect(name, all.toString());
        if (!needs(script, to)) return status(unknown(script) ? "skip" : "same");
        String hash = sha1("COMM", name, String.join("\u0001", src));
        Map<String, Object> hit = cached("COMM", commSn, to, hash);
        Map<String, Object> res = hit != null ? hit : translateCommunity(commSn, src, to, hash, me);
        return "ok".equals(res.get("status")) ? communityOut(res, rulesVisible) : res;
    }

    private Map<String, Object> translateCommunity(long commSn, List<String> src, String to, String hash, Long me) {
        List<Integer> idx = new ArrayList<>();
        List<String> send = new ArrayList<>();
        for (int i = 0; i < src.size(); i++) if (!src.get(i).isBlank()) { idx.add(i); send.add(TranslateMarkup.plainOut(src.get(i))); }
        List<Translator.Out> out = call(send, to, me);
        if (out == null) return status("unavailable");
        String from = out.getFirst().from();
        for (Translator.Out o : out) if (o.from() != null && !sameLang(o.from(), to)) { from = o.from(); break; }   // 이름만 영어인 한국어 커뮤니티 같은 경우 — 다른 칸으로 본다
        if (sameLang(from, to)) return save("COMM", commSn, to, hash, from, true, null, null);
        String[] tr = new String[src.size()];
        for (int i = 0; i < src.size(); i++) tr[i] = "";
        for (int k = 0; k < idx.size(); k++) tr[idx.get(k)] = TranslateMarkup.plainBack(out.get(k).html());
        List<Map<String, String>> rules = new ArrayList<>();
        for (int i = 2; i + 1 < tr.length; i += 2) rules.add(Map.of("title", tr[i], "body", tr[i + 1]));
        String body;
        try { body = JSON.writeValueAsString(Map.of("desc", tr[1], "rules", rules)); }
        catch (Exception e) { return status("unavailable"); }
        return save("COMM", commSn, to, hash, from, false, tr[0], body);
    }

    /**
     * 탐색·목록 카드의 커뮤니티 이름·소개 여러 개 — 결과는 커뮤니티 번호 → {status, name, desc, from}.
     * 규칙은 빼고(카드엔 안 보인다) 따로 기억한다(COMMC). 이름·소개는 비공개 커뮤니티도 탐색에 보이므로 누구에게나 준다.
     */
    public Map<Long, Map<String, Object>> communityCards(List<Long> commSns, Long me, String to) {
        List<Long> want = commSns.stream().distinct().limit(MAX_TITLES).toList();
        Map<Long, Map<String, Object>> res = new LinkedHashMap<>();
        if (want.isEmpty()) return res;
        String in = String.join(",", want.stream().map(String::valueOf).toList());
        List<Long> sendSn = new ArrayList<>();
        List<String> sendHash = new ArrayList<>(), sendText = new ArrayList<>();
        List<Integer> sendAt = new ArrayList<>();   // 커뮤니티마다 보낸 칸 수 — 이름만(1) 또는 이름·소개(2)
        jdbc.query("SELECT comm_sn, comm_dspl_nm, comm_nm, comm_desc, lang FROM knowra_community.tbl_comm WHERE comm_sn IN (" + in + ") AND stat = 'Y' AND actvtn_yn = 'Y'", rs -> {
            long sn = rs.getLong(1);
            String name = rs.getString(2) != null && !rs.getString(2).isBlank() ? rs.getString(2) : rs.getString(3);
            String desc = rs.getString(4) == null ? "" : rs.getString(4);
            String script = rs.getString(5) != null ? rs.getString(5) : LangDetect.detect(name, desc);
            if (!needs(script, to)) { res.put(sn, status(unknown(script) ? "skip" : "same")); return; }
            String hash = sha1("COMMC", name, desc);
            Map<String, Object> hit = cached("COMMC", sn, to, hash);
            if (hit != null) { res.put(sn, cardOut(hit)); return; }
            sendSn.add(sn); sendHash.add(hash);
            sendText.add(TranslateMarkup.titleOut(name));
            if (desc.isBlank()) sendAt.add(1); else { sendAt.add(2); sendText.add(TranslateMarkup.plainOut(desc)); }
        });
        if (sendSn.isEmpty()) return res;
        List<Translator.Out> out = call(sendText, to, me);
        int k = 0;
        for (int i = 0; i < sendSn.size(); i++) {
            long sn = sendSn.get(i);
            int n = sendAt.get(i);
            if (out == null) { res.put(sn, status("unavailable")); continue; }
            Translator.Out nm = out.get(k), ds = n > 1 ? out.get(k + 1) : null;
            k += n;
            String from = ds != null && ds.from() != null ? ds.from() : nm.from();
            res.put(sn, cardOut(sameLang(from, to) ? save("COMMC", sn, to, sendHash.get(i), from, true, null, null)
                    : save("COMMC", sn, to, sendHash.get(i), from, false, TranslateMarkup.titleBack(nm.html()), ds == null ? "" : TranslateMarkup.plainBack(ds.html()))));
        }
        return res;
    }

    /** 기억해 둔 꼴(title·body)을 카드 꼴(name·desc)로 */
    private static Map<String, Object> cardOut(Map<String, Object> m) {
        if (!"ok".equals(m.get("status"))) return m;
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("status", "ok"); o.put("from", m.get("from")); o.put("name", m.get("title")); o.put("desc", m.get("body"));
        return o;
    }

    /** 기억해 둔 꼴(title = 이름, body = {desc, rules} JSON)을 화면이 쓰는 꼴로. 규칙을 못 보는 사람에게는 규칙을 빼고 */
    private static Map<String, Object> communityOut(Map<String, Object> m, boolean rulesVisible) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("status", "ok");
        o.put("from", m.get("from"));
        o.put("name", m.get("title"));
        try {
            var b = JSON.readTree((String) m.get("body"));
            o.put("desc", b.path("desc").asText(""));
            List<Map<String, String>> rules = new ArrayList<>();
            if (rulesVisible) for (var r : b.path("rules")) rules.add(Map.of("title", r.path("title").asText(""), "body", r.path("body").asText("")));
            o.put("rules", rules);
        } catch (Exception e) { return status("unavailable"); }
        return o;
    }

    /**
     * 모임 제목·설명 여러 개 — 결과는 모임 번호 → {status, title, body, from}. 모임 목록·상세와 같은 기준으로 볼 수 있는 것만
     * (비공개 커뮤니티는 멤버만, 취소된 모임도 목록에 보이므로 번역한다). 설명이 비었으면 제목만 보낸다.
     */
    public Map<Long, Map<String, Object>> events(List<Long> eventSns, Long me, String to) {
        List<Long> want = eventSns.stream().distinct().limit(MAX_TITLES).toList();
        Map<Long, Map<String, Object>> res = new LinkedHashMap<>();
        if (want.isEmpty()) return res;
        String in = String.join(",", want.stream().map(String::valueOf).toList());
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT event_sn, comm_sn, title, descr FROM knowra_community.tbl_comm_event WHERE event_sn IN (" + in + ")");
        Map<Long, Boolean> readable = new java.util.HashMap<>();
        for (Map<String, Object> r : rows) {
            long sn = ((Number) r.get("event_sn")).longValue(), comm = ((Number) r.get("comm_sn")).longValue();
            if (!readable.computeIfAbsent(comm, c -> { try { wiki.requireReadable(c, me); return true; } catch (IllegalArgumentException e) { return false; } })) continue;
            String title = (String) r.get("title"), descr = r.get("descr") == null ? "" : (String) r.get("descr");
            String script = LangDetect.detect(title, descr);
            if (!needs(script, to)) { res.put(sn, status(unknown(script) ? "skip" : "same")); continue; }
            String hash = sha1("EVT", title, descr);
            Map<String, Object> hit = cached("EVT", sn, to, hash);
            if (hit != null) { res.put(sn, hit); continue; }
            // 모임마다 따로 부른다 — 한 화면에 모임은 많아야 몇 개고, 제목·설명 짝이 어긋날 일이 없다
            List<String> send = descr.isBlank() ? List.of(TranslateMarkup.titleOut(title)) : List.of(TranslateMarkup.titleOut(title), TranslateMarkup.plainOut(descr));
            List<Translator.Out> out = call(send, to, me);
            if (out == null) { res.put(sn, status("unavailable")); continue; }
            String from = out.size() > 1 && out.get(1).from() != null ? out.get(1).from() : out.getFirst().from();
            res.put(sn, sameLang(from, to) ? save("EVT", sn, to, hash, from, true, null, null)
                    : save("EVT", sn, to, hash, from, false, TranslateMarkup.titleBack(out.getFirst().html()), out.size() > 1 ? TranslateMarkup.plainBack(out.get(1).html()) : ""));
        }
        return res;
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
        return plainMany("CMT", texts, to, me, res);
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
        return plainMany("MSG", texts, to, me, res);
    }

    /**
     * 운영진 문의 — 보낸 사람이거나 그 커뮤니티 운영진(OWNER·ADMIN)만. 남이 쓴 것만(제목은 보낸 사람이 썼다).
     * 결과 {subject: {status, body, from}?, list: {메시지번호: {status, body, from}}}
     */
    public Map<String, Object> modmail(long mailSn, Long me, String to) {
        if (me == null) throw new IllegalArgumentException("문의를 찾을 수 없어요.");
        List<Map<String, Object>> ms = jdbc.queryForList("SELECT comm_sn, user_sn, subject FROM knowra_community.tbl_comm_modmail WHERE mail_sn = ?", mailSn);
        if (ms.isEmpty()) throw new IllegalArgumentException("문의를 찾을 수 없어요.");
        Map<String, Object> m = ms.getFirst();
        long owner = ((Number) m.get("user_sn")).longValue();
        boolean mod = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_mbr WHERE comm_sn = ? AND user_sn = ? AND stat = 'ACTIVE' AND role IN ('OWNER','ADMIN'))""",
                Boolean.class, m.get("comm_sn"), me));
        if (owner != me && !mod) throw new IllegalArgumentException("문의를 찾을 수 없어요.");   // 있는지조차 알리지 않는다
        Map<Long, String> texts = new LinkedHashMap<>();
        jdbc.query("SELECT msg_sn, body FROM knowra_community.tbl_comm_modmail_msg WHERE mail_sn = ? AND sender_sn <> ? AND COALESCE(body, '') <> '' ORDER BY msg_sn LIMIT ?",
                rs -> { texts.put(rs.getLong(1), rs.getString(2)); }, mailSn, me, MAX_CMTS);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", plainMany("MML", texts, to, me, new LinkedHashMap<>()));
        if (owner != me) out.put("subject", plainMany("MMS", Map.of(mailSn, (String) m.get("subject")), to, me, new LinkedHashMap<>()).getOrDefault(mailSn, status("skip")));
        return out;
    }

    /** 일반 글자(댓글·메시지) 여럿을 한 번에 — 캐시에 있는 건 그대로, 나머지만 업체에 */
    private Map<Long, Map<String, Object>> plainMany(String kind, Map<Long, String> texts, String to, Long me, Map<Long, Map<String, Object>> res) {
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
        List<Translator.Out> out = call(sendText, to, me);
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

    /**
     * 한도 안에서 업체를 부른다. 꺼져 있거나 한도를 넘거나 실패하면 null.
     * 로그인하지 않은 사람은 부르지 않는다 — 이미 해 둔 번역(로그인한 독자가 연 글)만 같이 본다.
     * 봇·크롤러가 글을 훑어도 이달 한도가 줄지 않는다.
     */
    private List<Translator.Out> call(List<String> html, String to, Long me) {
        Translator t = translator.getIfAvailable();
        if (t == null || me == null) return null;
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

    /** 지운 글·댓글·메시지의 번역본도 지운다 — 원문이 사라졌는데 사본이 남으면 안 된다. 검색어 번역은 30일만 */
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
        jdbc.update("""
                DELETE FROM knowra_com.tbl_translation t WHERE t.target_kind = 'MML'
                AND NOT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_modmail_msg x WHERE x.msg_sn = t.target_sn)""");
        jdbc.update("""
                DELETE FROM knowra_com.tbl_translation t WHERE t.target_kind = 'MMS'
                AND NOT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_modmail x WHERE x.mail_sn = t.target_sn)""");
        jdbc.update("""
                DELETE FROM knowra_com.tbl_translation t WHERE t.target_kind IN ('COMM', 'COMMC')
                AND NOT EXISTS (SELECT 1 FROM knowra_community.tbl_comm c WHERE c.comm_sn = t.target_sn AND c.stat = 'Y' AND c.actvtn_yn = 'Y')""");
        jdbc.update("""
                DELETE FROM knowra_com.tbl_translation t WHERE t.target_kind = 'EVT'
                AND NOT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_event e WHERE e.event_sn = t.target_sn)""");
        // 소개를 지웠거나 탈퇴했으면(탈퇴는 소개를 비운다) 번역본도
        jdbc.update("""
                DELETE FROM knowra_com.tbl_translation t WHERE t.target_kind = 'BIO'
                AND NOT EXISTS (SELECT 1 FROM knowra_user.tbl_user u WHERE u.user_sn = t.target_sn AND COALESCE(u.bio, '') <> '')""");
        jdbc.update("""
                DELETE FROM knowra_com.tbl_translation t WHERE t.target_kind = 'WIKI'
                AND NOT EXISTS (SELECT 1 FROM knowra_community.tbl_comm_page g WHERE g.page_sn = t.target_sn)""");
        jdbc.update("DELETE FROM knowra_com.tbl_query_translation WHERE created_at < now() - INTERVAL '30 days'");
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
