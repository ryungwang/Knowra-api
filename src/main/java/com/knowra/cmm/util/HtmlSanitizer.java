package com.knowra.cmm.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * 글 본문·커뮤니티 위키 HTML 정리 — 저장하기 <b>전에</b> 허용 목록 밖을 걷어낸다.
 *
 * <p>예전엔 편집기(Tiptap)가 만든 HTML 을 그대로 믿고 저장했다. 하지만 API 는 편집기를 거치지 않고도
 * 부를 수 있어서, {@code <img src=x onerror=...>} 같은 본문을 넣으면 그 글을 여는 모든 사람(운영진 포함)의
 * 브라우저에서 스크립트가 돌았다(저장형 XSS). 화면도 DOMPurify 로 한 번 더 거른다(이미 저장된 옛 글 대비).
 *
 * <p>허용 = 편집기가 실제로 만드는 것만: 문단·줄바꿈·굵게·기울임·취소선·밑줄·코드·인용·목록·제목(h1~h3)·
 * 구분선·링크(http/https/mailto)·이미지(http/https). 속성은 링크 href·rel·target, 이미지 src·alt 뿐.
 * on* 이벤트 속성, style, script·iframe·object, javascript: 주소는 전부 사라진다.
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {}

    private static final Safelist SAFE = new Safelist()
            .addTags("p", "br", "strong", "b", "em", "i", "s", "u", "code", "pre", "blockquote",
                     "ul", "ol", "li", "h1", "h2", "h3", "hr", "a", "img", "span")
            .addAttributes("a", "href", "rel", "target")
            .addAttributes("img", "src", "alt")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow");

    private static final Document.OutputSettings OUT = new Document.OutputSettings().prettyPrint(false);

    public static String clean(String html) {
        if (html == null || html.isEmpty()) return html;
        return Jsoup.clean(html, "", SAFE, OUT);
    }
}
