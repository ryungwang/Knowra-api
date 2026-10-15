package com.knowra.safety.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 폼(multipart)으로 오는 쓰기의 금칙어 검사 — {@link ContentFilterAdvice} 는 JSON 본문만 본다.
 * 프로필(닉네임·소개)과 커뮤니티(이름·표시 이름·소개)는 사진과 함께 폼으로 와서 지금까지 검사 밖이었다.
 */
@Component
@RequiredArgsConstructor
public class ContentFilterParamInterceptor implements HandlerInterceptor {

    static final Map<String, String[]> TARGETS = Map.of(
            "/api/user/setUserInfo",          new String[]{"nickName", "name", "bio"},
            "/api/community/setCommunity",    new String[]{"commNm", "commDsplNm", "commDesc"},
            "/api/community/updateCommunity", new String[]{"commDsplNm", "commDesc"}
    );

    private final ContentFilter filter;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String[] fields = TARGETS.get(req.getRequestURI());
        if (fields == null || !"POST".equalsIgnoreCase(req.getMethod())) return true;
        StringBuilder all = new StringBuilder();
        for (String f : fields) if (req.getParameter(f) != null) all.append(req.getParameter(f)).append('\n');
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long me = auth != null && auth.getPrincipal() instanceof Long u ? u : null;
        boolean admin = auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
        String why = filter.check(me, admin, ContentFilter.Kind.TEXT, all.toString(), false);
        if (why == null) return true;
        // ContentFilterAdvice 와 같은 모양의 실패 응답 — 화면은 resultMessage 를 그대로 띄운다
        res.setContentType("application/json;charset=UTF-8");
        String msg = why.replace("\\", "\\\\").replace("\"", "\\\"");
        res.getOutputStream().write(("{\"resultCode\":800,\"resultMessage\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8));
        return false;
    }
}
