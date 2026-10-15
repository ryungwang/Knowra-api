package com.knowra.cmm.i18n;

import com.knowra.cmm.model.ResultVO;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 응답의 resultMessage 를 요청 언어로 — 나가기 직전 한 곳에서.
 * 서비스 곳곳의 {@code setResultMessage("…")}(200곳이 넘는다)를 하나하나 고치지 않아도, 원문이 표에 있으면 번역된다.
 * 변수가 섞인 문장은 원문 그대로라 표에 없다 — 그런 곳은 만드는 자리에서 {@link Msg#t} 를 쓴다(이미 번역된 문장은 표에 없어 그대로 나간다).
 */
@RestControllerAdvice
public class TranslateResultAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;   // ResponseEntity<ResultVO> 로 감싼 것도 오므로 모양은 아래에서 본다
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ResultVO r && r.getResultMessage() != null) {
            String lang = Lang.fromHeader(request.getHeaders().getFirst("Accept-Language"));
            if (!Lang.KO.equals(lang)) r.setResultMessage(Msg.tl(lang, r.getResultMessage()));
        }
        return body;
    }
}
