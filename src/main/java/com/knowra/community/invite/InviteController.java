package com.knowra.community.invite;

import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/community/invite")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService service;

    /** body: commSn → code, expiresAt */
    @PostMapping("/create")
    public ResultVO create(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return service.create(b, me); }

    /** 누구나 — 초대 받는 화면 */
    @GetMapping("/{code}")
    public ResultVO view(@PathVariable String code, @CurrentUser Long me) { return service.view(code, me); }

    /** body: code, agreeRules */
    @PostMapping("/accept")
    public ResultVO accept(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return service.accept(b, me); }

    /** body: commSn — 내가 만든 링크 전부 취소 */
    @PostMapping("/revoke")
    public ResultVO revoke(@RequestBody Map<String, Object> b, @CurrentUser Long me) { return service.revoke(b, me); }
}
