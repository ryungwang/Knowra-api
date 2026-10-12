package com.knowra.community.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.community.entity.TblComm;
import com.knowra.community.entity.TblCommPost;
import com.knowra.community.service.CommunityPostService;
import com.knowra.community.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    @PostMapping("/setCommPost")
    public ResultVO setCommPost(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityPostService.setCommPost(params, me);
    }



    @PostMapping("/getCommPostList")
    public ResultVO getCommPostList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityPostService.getCommPostList(params, me);
    }

    @GetMapping("/getCommPost")
    public ResultVO getCommPost(@ModelAttribute TblCommPost tblCommPost, @CurrentUser Long me) {
        return communityPostService.getCommPost(tblCommPost, me);
    }

    @PostMapping("/setCommPostLike")
    public ResultVO setCommPostLike(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityPostService.setCommPostLike(params, me);
    }

    @PostMapping("/setCommPostCmt")
    public ResultVO setCommPostCmt(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityPostService.setCommPostCmt(params, me);
    }

    @PostMapping("/getCommPostCmtList")
    public ResultVO getCommPostCmtList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityPostService.getCommPostCmtList(params, me);
    }

    @PostMapping("/setCommPostCmtReact")
    public ResultVO setCommPostCmtReact(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityPostService.setCommPostCmtReact(params, me);
    }



}
