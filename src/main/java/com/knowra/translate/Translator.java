package com.knowra.translate;

import java.util.List;

/**
 * 번역 업체. 구현은 {@code knowra.translate.provider} 로 고른다 — none(기본, 번역 안 함) | azure.
 * 넣는 글은 HTML 이다 — 태그와 {@code class="notranslate"} 는 그대로 돌아온다({@link TranslateMarkup}).
 */
public interface Translator {

    /** 결과는 같은 순서. 실패하면 예외 */
    List<Out> translate(List<String> html, String to);

    /** from = 업체가 판별한 원문 언어(ko·en·zh-Hans …) */
    record Out(String html, String from) {}
}
