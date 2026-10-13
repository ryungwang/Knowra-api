package com.knowra.safety.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.safety.service.SafetyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 신고 · 차단 · 숨기기. 전부 로그인 필수. */
@RestController
@RequestMapping("/api/safety")
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    /** body: targetKind(POST|CMT|USER), targetSn, reason(SPAM|ABUSE|SEXUAL|ILLEGAL|PRIVACY|OTHER), detail? */
    @PostMapping("/report")
    public ResultVO report(@RequestBody Map<String, Object> params, @CurrentUser Long me) { return safetyService.setReport(params, me); }

    /** body: nickName, block */
    @PostMapping("/block")
    public ResultVO block(@RequestBody Map<String, Object> params, @CurrentUser Long me) { return safetyService.setBlock(params, me); }

    @PostMapping("/blockList")
    public ResultVO blockList(@CurrentUser Long me) { return safetyService.getBlockList(me); }

    /** body: postSn, hide */
    @PostMapping("/hidePost")
    public ResultVO hidePost(@RequestBody Map<String, Object> params, @CurrentUser Long me) { return safetyService.setPostHide(params, me); }

    @PostMapping("/hideList")
    public ResultVO hideList(@CurrentUser Long me) { return safetyService.getHideList(me); }
}
