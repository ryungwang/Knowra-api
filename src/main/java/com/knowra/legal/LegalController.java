package com.knowra.legal;

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

/** 약관 판·최소 나이(누구나), 내 동의 상태·다시 동의(로그인) */
@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
public class LegalController {

    private final LegalService service;

    @GetMapping("/info")
    public ResultVO info() { return run(service::info); }

    @GetMapping("/status")
    public ResultVO status(@CurrentUser Long me) { return run(() -> service.status(me)); }

    /** body: agreeTerms, agreePrivacy, ageConfirm */
    @PostMapping("/agree")
    public ResultVO agree(@RequestBody Map<String, Object> b, @CurrentUser Long me, HttpServletRequest req) {
        return run(() -> service.agree(me, b, ComUtil.extractClientIp(req)));
    }

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
