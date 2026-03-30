package com.knowra.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ComUtil {

    /**
     * 클라이언트 실제 IP 추출
     * <p>리버스 프록시 환경을 고려해 X-Forwarded-For → X-Real-IP → RemoteAddr 순으로 확인한다.</p>
     *
     * @param request HttpServletRequest
     * @return 클라이언트 IP 문자열
     */
    public static String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        // X-Forwarded-For는 "클라이언트, 프록시1, 프록시2" 형태이므로 첫 번째 값만 사용
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        return ip;
    }
}
