package com.knowra.admin.controller;

import com.knowra.admin.service.ContentGenerationService;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ContentGenerationService contentGenerationService;
    private final com.knowra.admin.service.AdminLogService adminLog;

    /** 전체 실행 (커뮤니티 + 게시글 + 댓글) */
    @PostMapping("/generate")
    public ResultVO generate(@com.knowra.cmm.security.CurrentUser Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            resultVO.putResult("summary", contentGenerationService.generate());
            adminLog.record(me, "CONTENT_GENERATE", null, null, com.knowra.community.modlog.ModLogDetail.of(java.util.Map.of("scope", "all")));
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        }
        return resultVO;
    }

    /** 유저만 생성 */
    @PostMapping("/generate/user")
    public ResultVO generateUser(@com.knowra.cmm.security.CurrentUser Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            int count = contentGenerationService.generateUser();
            adminLog.record(me, "CONTENT_GENERATE", null, null, com.knowra.community.modlog.ModLogDetail.of(java.util.Map.of("users", count)));
            resultVO.putResult("userCount", count);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        }
        return resultVO;
    }

    /** 커뮤니티만 생성 */
    @PostMapping("/generate/community")
    public ResultVO generateCommunity(@com.knowra.cmm.security.CurrentUser Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            int count = contentGenerationService.generateCommunity();
            adminLog.record(me, "CONTENT_GENERATE", null, null, com.knowra.community.modlog.ModLogDetail.of(java.util.Map.of("communities", count)));
            resultVO.putResult("communityCount", count);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        }
        return resultVO;
    }
}
