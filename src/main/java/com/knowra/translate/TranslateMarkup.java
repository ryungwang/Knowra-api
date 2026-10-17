package com.knowra.translate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 번역 업체에 보내기 전·후의 글 손질 — 번역하면 안 되는 것을 지킨다.
 * <ul>
 *   <li>멘션 {@code @닉네임}·링크 — 번역되면 눌러도 안 간다 → {@code <span class="notranslate">}</li>
 *   <li>스포일러 {@code ||가림||} — 속은 번역하되 표시는 남아야 한다 → {@code <span class="ksp">} 로 감쌌다 되돌린다</li>
 *   <li>글의 코드({@code <pre>·<code>}) — 번역하면 코드가 깨진다 → notranslate</li>
 * </ul>
 * 댓글·제목은 일반 글자라 HTML 로 바꿔 보내고(꺾쇠·& 이스케이프, 줄바꿈 → br) 돌아오면 되돌린다.
 */
final class TranslateMarkup {

    private TranslateMarkup() { }

    private static final String NT_OPEN = "<span class=\"notranslate\">", SP_OPEN = "<span class=\"ksp\">";
    private static final Pattern KEEP = Pattern.compile(
            "(https?://[^\\s<>\"]+)|((?<![\\p{L}\\p{M}\\p{N}._@-])@[\\p{L}\\p{M}\\p{N}._-]{2,30})");
    private static final Pattern SPOILER = Pattern.compile("\\|\\|(.+?)\\|\\|");
    private static final Pattern NT_BACK = Pattern.compile("<span class=\"notranslate\">(.*?)</span>", Pattern.DOTALL);
    private static final Pattern SP_BACK = Pattern.compile("<span class=\"ksp\">(.*?)</span>", Pattern.DOTALL);
    private static final Pattern BR = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_TAG = Pattern.compile("<(pre|code)\\b([^>]*)>", Pattern.CASE_INSENSITIVE);

    /** 댓글(일반 글자) → 보낼 HTML */
    static String plainOut(String text) {
        String s = escape(text == null ? "" : text);
        s = KEEP.matcher(s).replaceAll(m -> Matcher.quoteReplacement(NT_OPEN + m.group() + "</span>"));
        s = SPOILER.matcher(s).replaceAll(m -> Matcher.quoteReplacement(SP_OPEN + m.group(1) + "</span>"));
        return s.replace("\r\n", "\n").replace("\n", "<br>");
    }

    /** 돌아온 HTML → 댓글(일반 글자) */
    static String plainBack(String html) {
        String s = NT_BACK.matcher(html).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
        s = SP_BACK.matcher(s).replaceAll(m -> Matcher.quoteReplacement("||" + m.group(1) + "||"));
        s = BR.matcher(s).replaceAll("\n");
        return unescape(s.replaceAll("<[^>]+>", ""));
    }

    /** 제목 — 줄바꿈·멘션 없이 글자만 */
    static String titleOut(String title) { return escape(title == null ? "" : title); }

    static String titleBack(String html) { return unescape(html.replaceAll("<[^>]+>", "")).trim(); }

    /** 글 본문(HTML) → 보낼 HTML */
    static String htmlOut(String html) {
        String s = html == null ? "" : html;
        s = CODE_TAG.matcher(s).replaceAll(m -> {
            String attrs = m.group(2);
            String withNt = attrs.matches("(?is).*\\bclass\\s*=\\s*\".*")
                    ? attrs.replaceFirst("(?i)\\bclass\\s*=\\s*\"", "class=\"notranslate ")
                    : " class=\"notranslate\"" + attrs;
            return Matcher.quoteReplacement("<" + m.group(1) + withNt + ">");
        });
        return SPOILER.matcher(s).replaceAll(m -> Matcher.quoteReplacement(SP_OPEN + m.group(1) + "</span>"));
    }

    /** 돌아온 HTML → 글 본문 */
    static String htmlBack(String html) {
        String s = SP_BACK.matcher(html).replaceAll(m -> Matcher.quoteReplacement("||" + m.group(1) + "||"));
        return s.replace(" class=\"notranslate\"", "").replace("class=\"notranslate ", "class=\"");
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&");
    }
}
