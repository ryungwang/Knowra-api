package com.knowra.badge;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.user.repository.TblUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/badge/list?nickName= — 비로그인도 본다(얻은 것만). 본인이면 못 얻은 것과 진행도까지. */
@RestController
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badges;
    private final TblUserRepository users;

    @GetMapping("/api/badge/list")
    public ResultVO list(@RequestParam String nickName, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        var u = users.findByNickName(nickName).orElse(null);
        if (u == null) { r.setResultCode(ResponseCode.SELECT_ERROR.getCode()); r.setResultMessage("사용자를 찾을 수 없어요."); return r; }
        boolean mine = me != null && me == u.getUserSn();
        r.putResult("list", badges.view(u.getUserSn(), mine));
        r.putResult("total", BadgeCatalog.ALL.size());
        r.setResultCode(ResponseCode.SUCCESS.getCode());
        return r;
    }
}
