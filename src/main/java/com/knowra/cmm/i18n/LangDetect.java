package com.knowra.cmm.i18n;

import java.util.List;

/**
 * 글의 언어를 글자 체계로 가린다 — 번역 단추를 띄울지 정하는 정도면 된다(정확한 언어 판별이 아니다).
 * 한글이 있으면 ko, 가나가 있으면 ja, 한자만 있으면 zh, 로마자가 대부분이면 latn(영어·스페인어 등은 화면이 브라우저 판별기로 가린다).
 * 글자가 너무 적으면 null — 「ㅋㅋ」 한 줄에 번역 단추를 띄울 일은 없다.
 */
public final class LangDetect {
    private LangDetect() { }

    /** 화면이 고를 수 있는 커뮤니티 주 언어 */
    public static final List<String> COMM_LANGS = List.of("ko", "en", "ja", "zh-Hans", "zh-Hant", "es", "fr", "de", "pt", "ru", "vi", "id", "th", "ar", "hi");

    public static String commLang(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        return COMM_LANGS.contains(s) ? s : null;
    }

    public static String detect(String title, String html) {
        String text = ((title == null ? "" : title) + " " + (html == null ? "" : html.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ")));
        int hangul = 0, kana = 0, han = 0, latin = 0;
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            Character.UnicodeScript sc = Character.UnicodeScript.of(c);
            switch (sc) {
                case HANGUL -> hangul++;
                case HIRAGANA, KATAKANA -> kana++;
                case HAN -> han++;
                case LATIN -> latin++;
                default -> { }
            }
        }
        int total = hangul + kana + han + latin;
        if (total < 4) return null;
        if (hangul * 5 >= total) return "ko";      // 한국어 글에 영어 낱말이 섞여도 한국어
        if (kana > 0 && (kana + han) * 3 >= total) return "ja";
        if (han * 3 >= total) return "zh";
        if (latin * 2 >= total) return "latn";
        return null;
    }
}
