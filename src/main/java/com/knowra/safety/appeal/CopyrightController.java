package com.knowra.safety.appeal;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.common.util.ComUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Supplier;

/** 저작권 침해 신고 — 받기는 누구나(SecurityConfig), 처리는 관리자 */
@RestController
@RequiredArgsConstructor
public class CopyrightController {

    private final CopyrightService service;

    @PostMapping("/api/copyright/notice")
    public ResultVO submit(@RequestBody Map<String, Object> b, @CurrentUser Long me, HttpServletRequest req) {
        return run(() -> service.submit(b, me, ComUtil.extractClientIp(req)));
    }

    @PostMapping("/api/admin/console/copyrightList")
    public ResultVO list(@RequestBody(required = false) Map<String, Object> b) {
        return run(() -> service.adminList(b == null ? "OPEN" : String.valueOf(b.getOrDefault("stat", "OPEN"))));
    }

    /** body: noticeSn, action(ACT|REJECT), note */
    @PostMapping("/api/admin/console/copyrightHandle")
    public ResultVO handle(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return run(() -> service.adminHandle(b, me)); }

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
