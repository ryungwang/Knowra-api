package com.knowra.community.modmail;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.cmm.errlog.ErrorLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Supplier;

/** 운영진에게 문의. 전부 로그인 필요(SecurityConfig 기본). */
@RestController
@RequestMapping("/api/community/modmail")
@RequiredArgsConstructor
public class ModmailController {

    private final ModmailService service;

    /** body: commSn, subject, body → mailSn */
    @PostMapping("/create")
    public ResultVO create(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return run(() -> service.create(b, me)); }

    /** 보낸 사람이나 그 커뮤니티 운영진 */
    @GetMapping("/thread")
    public ResultVO thread(@RequestParam long mailSn, @CurrentUser Long me) { return run(() -> service.thread(mailSn, me)); }

    /** body: mailSn, body */
    @PostMapping("/reply")
    public ResultVO reply(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return run(() -> service.reply(b, me)); }

    /** body: mailSn, stat(OPEN|CLOSED) — 운영진만 */
    @PostMapping("/setStat")
    public ResultVO setStat(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return run(() -> service.setStat(b, me)); }

    /** 운영진 문의함 */
    @GetMapping("/inbox")
    public ResultVO inbox(@RequestParam long commSn, @RequestParam(defaultValue = "OPEN") String stat, @RequestParam(defaultValue = "0") int page, @CurrentUser Long me) {
        return run(() -> service.inbox(commSn, stat, page, me));
    }

    /** 내가 보낸 문의 */
    @GetMapping("/mine")
    public ResultVO mine(@RequestParam(defaultValue = "0") int page, @CurrentUser Long me) { return run(() -> service.mine(page, me)); }

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
