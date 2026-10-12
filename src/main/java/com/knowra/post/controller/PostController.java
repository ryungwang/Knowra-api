package com.knowra.post.controller;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.community.entity.TblCommPost;
import com.knowra.post.entity.TblPost;
import com.knowra.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping("/setPost")
    public ResultVO setPost(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPost(params, me);
    }

    @GetMapping("/getPost")
    public ResultVO getPost(@ModelAttribute TblPost tblPost, @CurrentUser Long me) {
        return postService.getPost(tblPost, me);
    }

    @PostMapping("/viewPost")
    public ResultVO viewPost(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.viewPost(params, me);
    }

    @PostMapping("/setPostLike")
    public ResultVO setPostLike(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostLike(params, me);
    }

    @PostMapping("/setPostCmt")
    public ResultVO setPostCmt(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostCmt(params, me);
    }

    @PostMapping("/setPostCmtReact")
    public ResultVO setPostCmtReact(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostCmtReact(params, me);
    }

    @PostMapping("/setPostDel")
    public ResultVO setPostDel(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostDel(params, me);
    }

    @PostMapping("/getPostCmtList")
    public ResultVO getPostCmtList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.getPostCmtList(params, me);
    }

    @PostMapping("/setPostSave")
    public ResultVO setPostSave(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.setPostSave(params, me);
    }

    @PostMapping("/getPostSaveList")
    public ResultVO getPostSaveList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return postService.getPostSaveList(params, me);
    }

}
