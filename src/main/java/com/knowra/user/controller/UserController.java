package com.knowra.user.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.community.entity.TblComm;
import com.knowra.community.service.CommunityService;
import com.knowra.user.entity.TblUserStng;
import com.knowra.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final CommunityService communityService;

    @PostMapping("/api/user/getUserProfile")
    public ResultVO getUserProfile(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return userService.getUserProfile(params, me);
    }

    @PostMapping("/api/user/setUserInfo")
    public ResultVO setUserInfo(@RequestParam Map<String, Object> params,
                                @RequestParam(required = false) MultipartFile profileImage,
                                @CurrentUser Long me) {
        return userService.setUserInfo(params, profileImage, me);
    }

    @PostMapping("/api/user/setUserSetting")
    public ResultVO setUserSetting(@RequestBody TblUserStng tblUserStng,
                                   @CurrentUser Long me) {
        return userService.setUserSetting(tblUserStng, me);
    }

    @PostMapping("/api/user/getUserPostList")
    public ResultVO getUserPostList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return userService.getUserPostList(params, me);
    }

    @PostMapping("/api/user/getUserFollowerList")
    public ResultVO getUserFollowerList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return userService.getUserFollowerList(params, me);
    }

    @PostMapping("/api/user/getUserFollowingList")
    public ResultVO getUserFollowingList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return userService.getUserFollowingList(params, me);
    }

    @PostMapping("/api/user/setFollow")
    public ResultVO setFollow(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return userService.setFollow(params, me);
    }

    @PostMapping("/api/user/changePassword")
    public ResultVO changePassword(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return userService.changePassword(params, me);
    }

    @PostMapping("/api/user/deleteAccount")
    public ResultVO deleteAccount(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return userService.deleteAccount(params, me);
    }

    @PostMapping("/api/user/getMyTagList")
    public ResultVO getMyTagList(@CurrentUser Long me) {
        return userService.getMyTagList(me);
    }

    @PostMapping("/api/user/getMyTagPostList")
    public ResultVO getMyTagPostList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return userService.getMyTagPostList(params, me);
    }


}
