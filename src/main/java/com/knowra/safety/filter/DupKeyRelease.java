package com.knowra.safety.filter;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 「같은 내용 반복」 표시는 본문을 읽자마자 잡는다(ContentFilter). 그런데 그 뒤 저장이 실패하면
 * (가입 안 한 커뮤니티·잠긴 글 등) 표시만 남아, 고쳐서 다시 올려도 10분 동안 「같은 내용」으로 막혔다.
 * 응답이 성공이 아니면 이번 요청이 잡은 표시를 푼다.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class DupKeyRelease implements ResponseBodyAdvice<Object> {

    private final ContentFilter filter;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) { return true; }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest req, ServerHttpResponse res) {
        if (!(req instanceof ServletServerHttpRequest s)) return body;
        HttpServletRequest r = s.getServletRequest();
        if (r.getAttribute(ContentFilter.DUP_KEY_ATTR) instanceof String key) {
            boolean ok = body instanceof ResultVO v && String.valueOf(ResponseCode.SUCCESS.getCode()).equals(String.valueOf(v.getResultCode()));
            if (!ok) filter.releaseDup(key);
            r.removeAttribute(ContentFilter.DUP_KEY_ATTR);
        }
        return body;
    }
}
