package com.knowra.cmm.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Locale;

/**
 * 서비스 언어 5개 — 화면 src/i18n/index.js 의 LANGS 와 같은 코드다.
 * 한국어가 원문이고, 모르는 언어는 영어로 본다(화면과 같은 규칙).
 */
public final class Lang {

    public static final String KO = "ko", EN = "en", JA = "ja", ZH_HANS = "zh-Hans", ZH_HANT = "zh-Hant";
    public static final List<String> ALL = List.of(KO, EN, JA, ZH_HANS, ZH_HANT);
    public static final String DEFAULT = EN;

    private Lang() {}

    /** 언어 태그 하나를 5개 중 하나로. 모르면 null. 중국어는 글자 체계로 — TW·HK·MO·Hant → 번체 */
    public static String normalize(String tag) {
        if (tag == null || tag.isBlank()) return null;
        String t = tag.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (t.startsWith("zh")) return t.matches(".*(hant|-tw|-hk|-mo).*") ? ZH_HANT : ZH_HANS;
        String base = t.split("-")[0];
        for (String l : ALL) if (l.equals(base)) return l;
        return null;
    }

    /** Accept-Language 머리글(「ja,en-US;q=0.9」)에서 처음 맞는 것. 없으면 기본(영어) */
    public static String fromHeader(String header) {
        if (header == null || header.isBlank()) return DEFAULT;
        for (String part : header.split(",")) {
            String l = normalize(part.split(";")[0]);
            if (l != null) return l;
        }
        return DEFAULT;
    }

    /** 지금 요청의 언어 — 요청 밖(스케줄러 등)이면 한국어(원문) */
    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) return of(a.getRequest());
        return KO;
    }

    public static String of(HttpServletRequest req) {
        return fromHeader(req.getHeader("Accept-Language"));
    }
}
