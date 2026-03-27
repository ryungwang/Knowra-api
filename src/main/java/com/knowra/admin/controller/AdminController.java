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

    /** 전체 실행 (커뮤니티 + 게시글 + 댓글) */
    @PostMapping("/generate")
    public ResultVO generate() {
        ResultVO resultVO = new ResultVO();
        try {
            resultVO.putResult("summary", contentGenerationService.generate());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        }
        return resultVO;
    }

    /** 유저만 생성 */
    @PostMapping("/generate/user")
    public ResultVO generateUser() {
        ResultVO resultVO = new ResultVO();
        try {
            int count = contentGenerationService.generateUser();
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
    public ResultVO generateCommunity() {
        ResultVO resultVO = new ResultVO();
        try {
            int count = contentGenerationService.generateCommunity();
            resultVO.putResult("communityCount", count);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        }
        return resultVO;
    }
}
