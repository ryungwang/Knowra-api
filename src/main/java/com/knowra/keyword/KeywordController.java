package com.knowra.keyword;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 키워드 알림 — 로그인 필수 */
@RestController
@RequestMapping("/api/keyword")
@RequiredArgsConstructor
public class KeywordController {

    private final KeywordService keywords;

    @PostMapping("/list")
    public ResultVO list(@CurrentUser Long me) { return ok(me); }

    /** body: keyword */
    @PostMapping("/add")
    public ResultVO add(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        try { keywords.add(me, String.valueOf(b.get("keyword"))); }
        catch (IllegalArgumentException e) {
            ResultVO r = new ResultVO();
            r.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
            return r;
        }
        return ok(me);
    }

    /** body: keyword */
    @PostMapping("/remove")
    public ResultVO remove(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        keywords.remove(me, String.valueOf(b.get("keyword")));
        return ok(me);
    }

    private ResultVO ok(long me) {
        ResultVO r = new ResultVO();
        r.putResult("list", keywords.list(me));
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }
}
