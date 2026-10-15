package com.knowra.cmm.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;

/**
 * 서버 문구 번역. <b>한국어 원문이 곧 키다</b> — 코드는 한국어 그대로 읽히고, 번역은 classpath
 * {@code i18n/messages.json} 한 곳에 모인다: {@code {"원문": {"en": "...", "ja": "...", "zh-Hans": "...", "zh-Hant": "..."}}}.
 *
 * <p>변수는 {@code {0} {1}} 자리표시로: {@code Msg.t("「{0}」의 주인이에요.", name)}. 변수 뒤 조사는 두 꼴을 적는다 —
 * {@code "{0}이(가) 글을 올렸어요."} — 한국어일 때 {@link Josa} 가 받침을 보고 하나로 고른다. 번역이 없으면 원문(한국어)에
 * 변수를 채워 돌려준다 — 번역이 빠져도 문장이 깨지지 않는다. 한국어는 조사가 붙는 문장이 많아 원문이 그대로 맞다.
 */
public final class Msg {

    private static final Logger log = LoggerFactory.getLogger(Msg.class);
    private static volatile Map<String, Map<String, String>> table = load();

    private Msg() {}

    private static Map<String, Map<String, String>> load() {
        try (InputStream in = Msg.class.getClassLoader().getResourceAsStream("i18n/messages.json")) {
            if (in == null) return Map.of();
            return new ObjectMapper().readValue(in, new TypeReference<Map<String, Map<String, String>>>() {});
        } catch (Exception e) {
            log.warn("i18n/messages.json 을 읽지 못했다 — 원문(한국어)으로 답한다: {}", e.toString());
            return Map.of();
        }
    }

    /** 지금 요청의 언어로 */
    public static String t(String ko, Object... args) { return tl(Lang.current(), ko, args); }

    /** 정한 언어로 — 알림·푸시·메일처럼 받는 사람의 언어가 따로 있을 때 */
    public static String tl(String lang, String ko, Object... args) {
        if (ko == null) return null;
        String tpl = ko;
        if (lang != null && !Lang.KO.equals(lang)) {
            Map<String, String> row = table.get(ko);
            if (row != null) {
                String hit = row.get(lang);
                if (hit == null && Lang.ZH_HANT.equals(lang)) hit = row.get(Lang.ZH_HANS);   // 번체가 없으면 간체가 한국어보다 낫다
                if (hit == null) hit = row.get(Lang.EN);
                if (hit != null) tpl = hit;
            }
        }
        String out = fill(tpl, args);
        // 한국어는 원문에 적어 둔 「이(가)」「을(를)」을 앞말 받침대로 고른다(Josa)
        return lang == null || Lang.KO.equals(lang) ? Josa.resolve(out) : out;
    }

    public static boolean has(String ko) { return table.containsKey(ko); }

    /** {0}·{1} 만 채운다 — MessageFormat 은 작은따옴표를 특수 문자로 먹어 「It's」 같은 번역이 깨진다 */
    static String fill(String tpl, Object... args) {
        if (args == null || args.length == 0) return tpl;
        String s = tpl;
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    /** 테스트용 — 표를 바꿔 끼운다 */
    static void useTable(Map<String, Map<String, String>> t) { table = t; }
}
