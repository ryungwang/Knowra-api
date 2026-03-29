package com.knowra.user.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.community.service.CommunityService;
import com.knowra.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


import java.util.Map;

@RestController
@CrossOrigin("*")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final CommunityService communityService;

    @PostMapping("/api/user/getUserProfile")
    public ResultVO getUserProfile(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        return userService.getUserProfile(params, request.getHeader("Authorization"));
    }

    @PostMapping("/api/user/getUserPostList")
    public ResultVO getUserPostList(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        return userService.getUserPostList(params, request.getHeader("Authorization"));
    }

    @PostMapping("/api/user/getUserFollowerList")
    public ResultVO getUserFollowerList(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        return userService.getUserFollowerList(params, request.getHeader("Authorization"));
    }

    @PostMapping("/api/user/getUserFollowingList")
    public ResultVO getUserFollowingList(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        return userService.getUserFollowingList(params, request.getHeader("Authorization"));
    }

    @PostMapping("/api/user/setFollow")
    public ResultVO setFollow(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        return userService.setFollow(params, request.getHeader("Authorization"));
    }
}
