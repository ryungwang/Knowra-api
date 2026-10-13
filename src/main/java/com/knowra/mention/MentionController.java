package com.knowra.mention;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MentionController {

    private final MentionService mentionService;

    /** GET /api/user/mentionSearch?q= — @ 뒤에 친 글자로 사람 찾기. 로그인 필수. */
    @GetMapping("/api/user/mentionSearch")
    public ResultVO search(@RequestParam(value = "q", required = false) String q, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        r.putResult("list", mentionService.search(q, me));
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }
}
