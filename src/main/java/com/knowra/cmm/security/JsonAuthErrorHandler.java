package com.knowra.cmm.security;

import com.knowra.cmm.i18n.Lang;
import com.knowra.cmm.i18n.Msg;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증 실패(401)·권한 없음(403)을 <b>앱의 응답 형식({@code ResultVO} 모양)</b>으로 돌려준다.
 *
 * <p>기본 동작은 빈 본문 또는 HTML 이라 프론트 {@code fetchClient}가 JSON 파싱에서 죽는다.
 * 프론트는 {@code r.status === 401}을 보고 토큰 갱신을 한 번 시도하므로 상태코드가 핵심이고,
 * 본문은 사람이 읽을 메시지만 있으면 된다.
 */
@Component
public class JsonAuthErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, Msg.tl(Lang.of(request), "로그인이 필요합니다."));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, Msg.tl(Lang.of(request), "권한이 없습니다."));
    }

    private static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"resultCode\":" + status + ",\"resultMessage\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"result\":{}}");
    }
}
