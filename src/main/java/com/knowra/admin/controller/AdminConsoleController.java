package com.knowra.admin.controller;

import com.knowra.admin.service.AdminConsoleService;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 운영 관리자 콘솔. {@code /api/admin/**} 전체가 ROLE_ADMIN 전용(SecurityConfig). */
@RestController
@RequestMapping("/api/admin/console")
@RequiredArgsConstructor
public class AdminConsoleController {

    private final AdminConsoleService service;

    @PostMapping("/stats")
    public ResultVO stats() { return service.getStats(); }

    /** body: stat(OPEN|RESOLVED|DISMISSED), page */
    @PostMapping("/reportList")
    public ResultVO reportList(@RequestBody(required = false) Map<String, Object> params) { return service.getReportList(params == null ? Map.of() : params); }

    /** body: reportSn, action(RESOLVE|DISMISS|REOPEN) */
    @PostMapping("/handleReport")
    public ResultVO handleReport(@RequestBody Map<String, Object> params, @CurrentUser Long me) { return service.handleReport(params, me); }

    /** body: postSn */
    @PostMapping("/deletePost")
    public ResultVO deletePost(@RequestBody Map<String, Object> params, @CurrentUser Long me) { return service.deletePost(params, me); }

    /** body: postCmtSn */
    @PostMapping("/deleteCmt")
    public ResultVO deleteCmt(@RequestBody Map<String, Object> params, @CurrentUser Long me) { return service.deleteCmt(params, me); }

    /** body: msgSn — 신고된 DM 가리기 */
    @PostMapping("/hideMsg")
    public ResultVO hideMsg(@RequestBody Map<String, Object> params, @CurrentUser Long me) { return service.hideMsg(params, me); }

    /** body: q, page */
    @PostMapping("/userList")
    public ResultVO userList(@RequestBody(required = false) Map<String, Object> params) { return service.getUserList(params == null ? Map.of() : params); }

    /** body: userSn, suspend */
    @PostMapping("/setUserSuspend")
    public ResultVO setUserSuspend(@RequestBody Map<String, Object> params, @CurrentUser Long me) { return service.setUserSuspend(params, me); }
}
