package com.knowra.user;

import com.knowra.cmm.i18n.Lang;
import com.knowra.cmm.i18n.Msg;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 탈퇴한 사람의 표시 이름. 탈퇴하면 줄은 남기고(대화·글 자리의 주인) 이름을 「탈퇴한 사용자」·「탈퇴회원58」로 바꾼다
 * (AccountDeletionService). 그 글자가 DB 에 박혀 있어 외국어 사용자에게도 한국어로 보였다.
 *
 * <p>사용자 정보를 만드는 곳이 수십 군데라, 응답이 나가기 직전 한 곳(TranslateResultAdvice)에서 보는 사람 언어로 바꾼다.
 * 그래서 이 이름은 사람이 고를 수 없게 막는다(UserRules) — 아니면 「탈퇴회원5」로 지은 사람의 이름도 바뀐다.
 */
public final class DeletedUser {
    private DeletedUser() { }

    public static final String NAME = "탈퇴한 사용자";
    public static final String NICK_PREFIX = "탈퇴회원";
    private static final Pattern NICK = Pattern.compile("^" + NICK_PREFIX + "(\\d+)$");
    private static final int MAX_DEPTH = 8;

    /** 응답 안의 지도·목록을 훑어 탈퇴 표시 이름을 그 언어로 바꾼다. 한국어면 그대로 */
    public static void localize(Object node, String lang) {
        if (lang == null || Lang.KO.equals(lang)) return;
        walk(node, lang, 0);
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object node, String lang, int depth) {
        if (depth > MAX_DEPTH || node == null) return;
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Object v = e.getValue();
                if (v instanceof String s) {
                    String to = label(s, lang);
                    if (to != null) {
                        try { ((Map.Entry<Object, Object>) e).setValue(to); } catch (UnsupportedOperationException ignored) { /* 바꿀 수 없는 지도 — 그대로 */ }
                    }
                } else walk(v, lang, depth + 1);
            }
        } else if (node instanceof List<?> l) {
            for (Object v : l) walk(v, lang, depth + 1);
        }
    }

    static String label(String s, String lang) {
        if (NAME.equals(s)) return Msg.tl(lang, NAME);
        if (s.startsWith(NICK_PREFIX)) {
            Matcher m = NICK.matcher(s);
            if (m.matches()) return Msg.tl(lang, "탈퇴회원{0}", m.group(1));
        }
        return null;
    }
}
