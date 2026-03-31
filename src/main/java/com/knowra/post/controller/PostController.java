package com.knowra.post.controller;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResultVO;
import com.knowra.community.entity.TblCommPost;
import com.knowra.post.entity.TblPost;
import com.knowra.post.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping("/setPost")
    public ResultVO setPost(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return postService.setPost(params, token);
    }

    @GetMapping("/getPost")
    public ResultVO getPost(@ModelAttribute TblPost tblPost, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return postService.getPost(tblPost, token);
    }

    @PostMapping("/viewPost")
    public ResultVO viewPost(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return postService.viewPost(params, token);
    }

    @PostMapping("/setPostLike")
    public ResultVO setPostLike(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return postService.setPostLike(params, token);
    }

    @PostMapping("/setPostCmt")
    public ResultVO setPostCmt(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return postService.setPostCmt(params, token);
    }

    @PostMapping("/setPostCmtReact")
    public ResultVO setPostCmtReact(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return postService.setPostCmtReact(params, token);
    }

    @PostMapping("/setPostDel")
    public ResultVO setPostDel(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return postService.setPostDel(params, token);
    }

    @PostMapping("/getPostCmtList")
    public ResultVO getPostCmtList(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return postService.getPostCmtList(params, token);
    }

    @PostMapping("/setPostSave")
    public ResultVO setPostSave(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return postService.setPostSave(params, token);
    }

    @PostMapping("/getPostSaveList")
    public ResultVO getPostSaveList(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        return postService.getPostSaveList(params, request.getHeader("Authorization"));
    }

}
