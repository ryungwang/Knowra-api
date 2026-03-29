package com.knowra.post.controller;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResultVO;
import com.knowra.post.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

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
