package com.knowra.safety.appeal;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 운영 조치 보기·이의 제기(본인), 정지된 사람의 이의(로그인 실패 때 받은 확인표로 — SecurityConfig 에서 열어 둔다),
 * 관리자 재검토(/api/admin/** 는 ROLE_ADMIN 만).
 */
@RestController
@RequiredArgsConstructor
public class AppealController {

    private final ModDecisionService service;

    @GetMapping("/api/appeal/decision")
    public ResultVO view(@RequestParam long decisionSn, @CurrentUser Long me) { return run(() -> service.view(decisionSn, me)); }

    /** body: decisionSn, text */
    @PostMapping("/api/appeal/submit")
    public ResultVO submit(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return run(() -> service.appeal(b, me)); }

    @GetMapping("/api/appeal/suspended")
    public ResultVO viewSuspended(@RequestParam String ticket) { return run(() -> service.viewSuspended(ticket)); }

    /** body: ticket, text */
    @PostMapping("/api/appeal/suspended")
    public ResultVO submitSuspended(@RequestBody Map<String, Object> b) { return run(() -> service.appealSuspended(b)); }

    @PostMapping("/api/admin/console/appealList")
    public ResultVO adminList(@RequestBody(required = false) Map<String, Object> b) {
        return run(() -> service.adminList(b == null ? "OPEN" : String.valueOf(b.getOrDefault("stat", "OPEN"))));
    }

    /** body: decisionSn, result(UPHELD|REVERSED), note */
    @PostMapping("/api/admin/console/appealDecide")
    public ResultVO adminDecide(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return run(() -> service.adminDecide(b, me)); }

    private static ResultVO run(Supplier<Map<String, Object>> work) {
        ResultVO r = new ResultVO();
        try {
            work.get().forEach(r::putResult);
            r.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }
        return r;
    }
}
