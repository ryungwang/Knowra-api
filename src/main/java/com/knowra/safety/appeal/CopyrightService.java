package com.knowra.safety.appeal;

import com.knowra.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 저작권 침해 신고(게시 중단 요청) — 받기·관리자 처리.
 * 받아들이면 글을 내리고 {@link ModDecisionService} 로 글쓴이에게 사유(COPYRIGHT)를 알린다. 글쓴이의 반론(반대 통지)은 그 이의 제기다.
 */
@Service
@RequiredArgsConstructor
public class CopyrightService {

    /** 우리 글 주소에서 글 번호 — /posts/123 또는 /community/x/posts/123, 공유 주소 /s/p/123 */
    private static final Pattern POST_URL = Pattern.compile("/(?:posts|s/p)/(\\d+)");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final JdbcTemplate jdbc;
    private final PostService postService;
    private final ModDecisionService decisions;

    /**
     * 신고 받기 — 로그인 없어도. params: name, email, org?, workDesc, originalUrl?, targetUrl, goodFaith, accurate, signature
     * 진술 두 개(선의, 정확성·권한)에 동의하고 서명(이름)을 적어야 한다 — 법이 요구하는 요건이라 빠지면 받지 않는다.
     */
    @Transactional
    public Map<String, Object> submit(Map<String, Object> p, Long me, String ip) {
        String name = req(p.get("name"), 100, "이름을 적어 주세요.");
        String email = req(p.get("email"), 200, "연락받을 이메일을 적어 주세요.");
        if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("이메일 주소를 확인해 주세요.");
        String work = req(p.get("workDesc"), 2000, "침해된 저작물을 설명해 주세요.");
        String target = req(p.get("targetUrl"), 500, "문제가 된 글 주소를 적어 주세요.");
        String sig = req(p.get("signature"), 100, "서명으로 이름을 적어 주세요.");
        if (!yes(p.get("goodFaith")) || !yes(p.get("accurate"))) throw new IllegalArgumentException("필수 진술에 동의해 주세요.");
        Matcher m = POST_URL.matcher(target);
        if (!m.find()) throw new IllegalArgumentException("knowra 글 주소를 적어 주세요(예: …/posts/123).");
        long postSn = Long.parseLong(m.group(1));
        Long author = jdbc.query("SELECT user_sn FROM knowra_post.tbl_post WHERE post_sn = ? AND actvtn_yn = 'Y'", rs -> rs.next() ? rs.getLong(1) : null, postSn);
        if (author == null) throw new IllegalArgumentException("그 글을 찾을 수 없어요. 이미 내려갔을 수 있어요.");
        Long sn = jdbc.queryForObject("""
                INSERT INTO knowra_user.tbl_copyright_notice (claimant_name, claimant_email, claimant_org, work_desc, original_url, target_url,
                    target_post_sn, target_user_sn, signature, reporter_sn, ip)
                VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING notice_sn""", Long.class,
                name, email, opt(p.get("org"), 200), work, opt(p.get("originalUrl"), 500), target, postSn, author, sig, me, ip);
        return Map.of("noticeSn", sn);
    }

    /** 관리자 목록 — 글쓴이별 받아들인 신고 수(반복 침해 확인용)를 붙인다 */
    public Map<String, Object> adminList(String stat) {
        String st = "ACTED".equals(stat) || "REJECTED".equals(stat) ? stat : "OPEN";
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT n.*, p.post_ttl, u.nick_name,
                       (SELECT COUNT(*) FROM knowra_user.tbl_copyright_notice x WHERE x.target_user_sn = n.target_user_sn AND x.stat = 'ACTED') AS strikes
                FROM knowra_user.tbl_copyright_notice n
                LEFT JOIN knowra_post.tbl_post p ON p.post_sn = n.target_post_sn
                LEFT JOIN knowra_user.tbl_user u ON u.user_sn = n.target_user_sn
                WHERE n.stat = ? ORDER BY n.created_at ASC LIMIT 100""", st)) {
            Map<String, Object> o = new LinkedHashMap<>();
            for (String k : List.of("notice_sn", "claimant_name", "claimant_email", "claimant_org", "work_desc", "original_url", "target_url",
                    "target_post_sn", "signature", "stat", "note", "created_at", "post_ttl", "nick_name", "strikes"))
                o.put(camel(k), r.get(k));
            list.add(o);
        }
        return Map.of("list", list);
    }

    /** 처리. params: noticeSn, action(ACT|REJECT), note — ACT 면 글을 내리고 글쓴이에게 알린다 */
    @Transactional
    public Map<String, Object> adminHandle(Map<String, Object> p, long admin) {
        long sn = Long.parseLong(String.valueOf(p.get("noticeSn")));
        String action = String.valueOf(p.get("action"));
        Map<String, Object> n = jdbc.queryForList("SELECT * FROM knowra_user.tbl_copyright_notice WHERE notice_sn = ?", sn)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없어요."));
        if (!"OPEN".equals(n.get("stat"))) throw new IllegalArgumentException("이미 처리한 신고예요.");
        String note = opt(p.get("note"), 500);
        if ("ACT".equals(action)) {
            Long postSn = n.get("target_post_sn") == null ? null : ((Number) n.get("target_post_sn")).longValue();
            String title = postSn == null ? null : jdbc.query("SELECT post_ttl FROM knowra_post.tbl_post WHERE post_sn = ?", rs -> rs.next() ? rs.getString(1) : null, postSn);
            if (postSn != null && postService.adminDeletePost(postSn, admin) && n.get("target_user_sn") != null) {
                // 글쓴이에게 — 신고자 개인정보(이메일)는 넘기지 않고, 무엇이 문제인지만 알린다. 반론은 이의 제기로
                String detail = com.knowra.cmm.i18n.Msg.t("권리자의 저작권 침해 신고: {0}", String.valueOf(n.get("work_desc")));
                decisions.record(((Number) n.get("target_user_sn")).longValue(), "POST_REMOVE", postSn, "COPYRIGHT", detail, title, admin);
            }
            jdbc.update("UPDATE knowra_user.tbl_copyright_notice SET stat = 'ACTED', handled_by = ?, handled_at = now(), note = ? WHERE notice_sn = ?", admin, note, sn);
        } else if ("REJECT".equals(action)) {
            jdbc.update("UPDATE knowra_user.tbl_copyright_notice SET stat = 'REJECTED', handled_by = ?, handled_at = now(), note = ? WHERE notice_sn = ?", admin, note, sn);
        } else throw new IllegalArgumentException("잘못된 요청이에요.");
        return Map.of("stat", "ACT".equals(action) ? "ACTED" : "REJECTED");
    }

    private static String req(Object v, int max, String msg) {
        String s = v == null ? "" : String.valueOf(v).strip();
        if (s.isEmpty()) throw new IllegalArgumentException(msg);
        return s.length() > max ? s.substring(0, max) : s;
    }
    private static String opt(Object v, int max) {
        String s = v == null ? "" : String.valueOf(v).strip();
        if (s.isEmpty() || "null".equals(s)) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
    private static boolean yes(Object v) { return v != null && Boolean.parseBoolean(String.valueOf(v)); }
    private static String camel(String k) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (char c : k.toCharArray()) { if (c == '_') { up = true; continue; } b.append(up ? Character.toUpperCase(c) : c); up = false; }
        return b.toString();
    }
}
