package com.knowra.community.controller;

import com.knowra.cmm.model.ResultVO;
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
@CrossOrigin("*")
@RequiredArgsConstructor
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    @PostMapping("/setCommPost")
    public ResultVO setCommPost(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityPostService.setCommPost(params, token);
    }



    @PostMapping("/getCommPostList")
    public ResultVO getCommPostList(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityPostService.getCommPostList(params, token);
    }

    @GetMapping("/getCommPost")
    public ResultVO getCommPost(@ModelAttribute TblCommPost tblCommPost, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityPostService.getCommPost(tblCommPost, token);
    }

    @PostMapping("/setCommPostLike")
    public ResultVO setCommPostLike(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityPostService.setCommPostLike(params, token);
    }

    @PostMapping("/setCommPostCmt")
    public ResultVO setCommPostCmt(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityPostService.setCommPostCmt(params, token);
    }

    @PostMapping("/getCommPostCmtList")
    public ResultVO getCommPostCmtList(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityPostService.getCommPostCmtList(params, token);
    }

    @PostMapping("/setCommPostCmtReact")
    public ResultVO setCommPostCmtReact(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityPostService.setCommPostCmtReact(params, token);
    }



}
