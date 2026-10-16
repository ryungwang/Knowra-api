package com.knowra.tag;

import com.knowra.cmm.errlog.ErrorLog;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Supplier;

/** 태그 화면·팔로우. info·posts 는 로그인 전에도 보인다(SecurityConfig). */
@RestController
@RequestMapping("/api/tag")
@RequiredArgsConstructor
public class TagFollowController {

    private final TagFollowService service;

    @GetMapping("/info")
    public ResultVO info(@RequestParam String name, @CurrentUser Long me) { return run(() -> service.info(name, me)); }

    @GetMapping("/posts")
    public ResultVO posts(@RequestParam String name, @RequestParam(required = false) String cursor, @CurrentUser Long me) {
        return run(() -> service.posts(name, cursor, me));
    }

    /** body: name, follow */
    @PostMapping("/follow")
    public ResultVO follow(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return run(() -> service.follow(b, me)); }

    @GetMapping("/following")
    public ResultVO following(@CurrentUser Long me) { return run(() -> service.following(me)); }

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
            r.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            r.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return r;
    }
}
