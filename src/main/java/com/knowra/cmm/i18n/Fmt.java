package com.knowra.cmm.i18n;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** 날짜를 받는 사람의 언어·시간대로. 서버의 LocalDateTime 은 JVM 시간대 기준이다 */
public final class Fmt {

    private Fmt() {}

    public static Locale locale(String lang) {
        return switch (lang == null ? "" : lang) {
            case Lang.KO -> Locale.KOREAN;
            case Lang.JA -> Locale.JAPANESE;
            case Lang.ZH_HANS -> Locale.SIMPLIFIED_CHINESE;
            case Lang.ZH_HANT -> Locale.TRADITIONAL_CHINESE;
            default -> Locale.ENGLISH;
        };
    }

    public static ZoneId zone(String tz) {
        try { return tz == null || tz.isBlank() ? ZoneId.of("Asia/Seoul") : ZoneId.of(tz); }
        catch (Exception e) { return ZoneId.of("Asia/Seoul"); }
    }

    /** 「10월 18일 오후 11:25」 / 「Oct 18, 2026, 11:25 PM」 — 언어마다 그 나라 방식으로 */
    public static String dateTime(LocalDateTime serverTime, String lang, String tz) {
        if (serverTime == null) return "";
        var at = serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(zone(tz));
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale(lang)).format(at);
    }
}
