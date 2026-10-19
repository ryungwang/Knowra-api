package com.knowra.safety.service;

import com.knowra.cmm.i18n.LangDetect;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 신고 대상의 언어 — 검수자를 언어로 나누는 데 쓴다(V69). 글자 체계까지만 가린다: ko·ja·zh·latn, 모르면 und.
 * 영어와 스페인어는 갈리지 않는다 — 로마자를 읽는 검수자가 같이 본다.
 */
@Component
@RequiredArgsConstructor
public class ReportLang {
    public static final String UNKNOWN = "und";
    /** 검수자가 고를 수 있는 언어 */
    public static final List<String> GROUPS = List.of("ko", "ja", "zh", "latn");

    private final JdbcTemplate jdbc;

    public String of(String kind, long sn) {
        String lang = switch (kind) {
            case "POST" -> jdbc.query("SELECT lang, post_ttl, post_cntnt FROM knowra_post.tbl_post WHERE post_sn = ?",
                    rs -> !rs.next() ? null : rs.getString(1) != null ? rs.getString(1) : LangDetect.detect(rs.getString(2), rs.getString(3)), sn);
            case "CMT" -> jdbc.query("SELECT cmt_cntnt FROM knowra_post.tbl_post_cmt WHERE post_cmt_sn = ?",
                    rs -> rs.next() ? LangDetect.detect(null, rs.getString(1)) : null, sn);
            case "MSG" -> jdbc.query("SELECT msg_cntnt FROM knowra_user.tbl_chat_msg WHERE msg_sn = ?",
                    rs -> rs.next() ? LangDetect.detect(null, rs.getString(1)) : null, sn);
            // 사용자 신고 — 프로필·닉네임은 짧아 가릴 수 없다. 그 사람이 쓰는 화면 언어로 본다
            case "USER" -> jdbc.query("SELECT lang FROM knowra_user.tbl_user_stng WHERE user_sn = ?",
                    rs -> rs.next() ? ofUiLang(rs.getString(1)) : null, sn);
            default -> null;
        };
        return normalize(lang);
    }

    public static String ofText(String text) { return normalize(LangDetect.detect(null, text)); }

    /** 화면 언어(ko·en·ja·zh-Hans·zh-Hant…) → 글자 체계 */
    static String ofUiLang(String ui) {
        if (ui == null) return null;
        if (ui.startsWith("zh")) return "zh";
        return switch (ui) { case "ko" -> "ko"; case "ja" -> "ja"; default -> "latn"; };
    }

    static String normalize(String lang) { return lang != null && GROUPS.contains(lang) ? lang : UNKNOWN; }

    /** "ko,latn" → [ko, latn]. 비었거나 모르는 값뿐이면 빈 목록 = 전부 */
    public static List<String> parse(Object v) {
        if (v == null) return List.of();
        List<String> raw = v instanceof List<?> l ? l.stream().map(String::valueOf).toList() : Arrays.asList(String.valueOf(v).split(","));
        return GROUPS.stream().filter(g -> raw.stream().map(String::trim).anyMatch(g::equals)).toList();
    }
}
