package com.knowra.safety.filter;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * 글·댓글·DM 의 본문을 <b>읽자마자 한 곳에서</b> 검사한다. 저장 코드가 서비스마다 흩어져 있어
 * 거기마다 넣으면 하나를 빠뜨리기 쉽다. 걸리면 저장 전에 멈추고 평소 모양의 실패 응답을 준다.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ContentFilterAdvice extends RequestBodyAdviceAdapter {

    /** 경로 → (종류, 검사할 필드들, 수정인지 가리는 필드). */
    record Target(ContentFilter.Kind kind, String[] fields, String editKey) {}

    private static final Map<String, Target> TARGETS = Map.of(
            "/api/post/setPost",        new Target(ContentFilter.Kind.POST, new String[]{"postTtl", "postCntnt"}, "postSn"),
            "/api/post/setPostCmt",     new Target(ContentFilter.Kind.CMT,  new String[]{"cmtCntnt"}, null),
            "/api/post/setPostCmtEdit", new Target(ContentFilter.Kind.CMT,  new String[]{"cmtCntnt"}, "postCmtSn"),
            "/api/chat/sendMsg",        new Target(ContentFilter.Kind.MSG,  new String[]{"msgCntnt"}, null)
    );

    static class Blocked extends RuntimeException {
        Blocked(String msg) { super(msg, null, false, false); }
    }

    private final ContentFilter filter;

    @Override
    public boolean supports(MethodParameter p, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return target() != null;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage in, MethodParameter p, Type t, Class<? extends HttpMessageConverter<?>> c) {
        Target tg = target();
        if (tg == null || !(body instanceof Map<?, ?> map)) return body;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long me = auth != null && auth.getPrincipal() instanceof Long u ? u : null;
        boolean admin = auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
        boolean isEdit = tg.editKey() != null && map.get(tg.editKey()) != null;
        StringBuilder all = new StringBuilder();
        for (String f : tg.fields()) if (map.get(f) != null) all.append(map.get(f)).append('\n');
        String why = filter.check(me, admin, tg.kind(), all.toString(), isEdit);
        if (why != null) throw new Blocked(why);
        return body;
    }

    @ExceptionHandler(Blocked.class)
    public ResultVO blocked(Blocked e) {
        ResultVO r = new ResultVO();
        r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
        r.setResultMessage(e.getMessage());
        return r;
    }

    private static Target target() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)) return null;
        return TARGETS.get(a.getRequest().getRequestURI());
    }
}
