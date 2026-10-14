package com.knowra.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ComUtil {

    /** 앞단 프록시가 몇 겹인지. {@code knowra.proxy.trusted-hops} — 아래 extractClientIp 참고. */
    private static volatile int trustedHops = 1;

    @org.springframework.beans.factory.annotation.Value("${knowra.proxy.trusted-hops:1}")
    void setTrustedHops(int hops) { trustedHops = Math.max(0, hops); }

    /**
     * 클라이언트 실제 IP.
     *
     * <p>⚠️ X-Forwarded-For 의 <b>첫 값은 클라이언트가 마음대로 쓸 수 있다.</b> 예전엔 그 값을 썼기
     * 때문에 헤더만 바꿔 보내면 IP 별 제한(로그인 실패·도배)을 얼마든지 피할 수 있었다.
     * 믿을 수 있는 건 <b>우리 프록시가 덧붙인 오른쪽 값들</b>뿐이다. 프록시가 한 겹(nginx 나
     * 로드밸런서 하나)이면 맨 오른쪽, 두 겹이면 오른쪽에서 두 번째가 실제 클라이언트다.
     * 프록시 없이 바로 노출하면 0 으로 둔다(헤더를 무시하고 접속 주소만 쓴다).
     */
    public static String extractClientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (trustedHops == 0) return remote;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            // X-Real-IP 만 넘기는 nginx 설정도 있다. 이걸 안 보면 모두가 프록시 IP 하나로 묶여
            // IP 별 제한이 사이트 전체에 걸린다. (프록시가 있다는 전제라 덮어쓴 값으로 믿는다)
            String real = request.getHeader("X-Real-IP");
            return real == null || real.isBlank() ? remote : real.trim();
        }
        String[] parts = xff.split(",");
        int idx = parts.length - trustedHops;
        if (idx < 0) return remote;
        String ip = parts[idx].trim();
        return ip.isEmpty() ? remote : ip;
    }

    public static String getStrValue(Object param) {
        String rtn ="";
        if (param != null) {
            rtn = param.toString();
        }
        return rtn;
    }

    public static String getStrValue(Map<String, Object> params, String key) {
        String rtn = params.get(key) == null ? "" : params.get(key).toString();
        return rtn;
    }
}
