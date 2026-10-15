package com.knowra.push.controller;

import com.knowra.cmm.i18n.Msg;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.push.service.VapidKeys;
import com.knowra.push.service.WebPushService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 웹 푸시 구독. 공개키 조회만 비로그인 허용, 나머지는 로그인 필수. */
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final WebPushService push;
    private final VapidKeys vapid;

    /** 서버 공개키 — null 이면 이 서버는 푸시가 꺼져 있다(프론트는 토글을 숨긴다). */
    @GetMapping("/publicKey")
    public ResultVO publicKey() {
        ResultVO r = new ResultVO();
        r.putResult("publicKey", vapid.publicKey());
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** body: endpoint, keys{p256dh, auth} — 브라우저 PushSubscription.toJSON() 그대로 */
    @PostMapping("/subscribe")
    @SuppressWarnings("unchecked")
    public ResultVO subscribe(@RequestBody Map<String, Object> body, @CurrentUser Long me, HttpServletRequest req) {
        ResultVO r = new ResultVO();
        try {
            Map<String, Object> keys = (Map<String, Object>) body.getOrDefault("keys", Map.of());
            push.subscribe(me, String.valueOf(body.get("endpoint")), String.valueOf(keys.get("p256dh")), String.valueOf(keys.get("auth")), req.getHeader("User-Agent"));
            r.putResult("devices", push.count(me));
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException | IllegalStateException e) {
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode()); r.setResultMessage(e.getMessage());
        } catch (Exception e) {
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode()); r.setResultMessage("구독 정보가 올바르지 않아요.");
        }
        return r;
    }

    /** body: endpoint */
    @PostMapping("/unsubscribe")
    public ResultVO unsubscribe(@RequestBody Map<String, Object> body, @CurrentUser Long me) {
        push.unsubscribe(me, String.valueOf(body.get("endpoint")));
        ResultVO r = new ResultVO();
        r.putResult("devices", push.count(me));
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** 내 모든 기기로 시험 알림 — 설정 화면의 「테스트 알림 보내기」. */
    @PostMapping("/test")
    public ResultVO test(@CurrentUser Long me) {
        int sent = push.sendToUser(me, Map.of("title", "knowra", "body", Msg.t("알림이 잘 와요. 이제 새 소식을 놓치지 않아요."), "url", "/", "tag", "knowra-test"));
        ResultVO r = new ResultVO();
        r.putResult("sent", sent);
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        if (sent == 0) r.setResultMessage("받을 수 있는 기기가 없어요. 이 기기에서 알림을 다시 켜 주세요.");
        return r;
    }
}
