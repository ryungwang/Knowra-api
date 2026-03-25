package com.knowra.user.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.community.service.CommunityService;
import com.knowra.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin("*")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final CommunityService communityService;

//    @PostMapping("/api/user/getUserProfile")
//    public ResultVO getUserProfile(@RequestBody Map<String, Object> params) {
//        return userService.getUserProfile(params);
//    }
//
//    @PostMapping("/api/user/setUserProfile")
//    public ResultVO setUserProfile(@RequestBody TblUser tblUser) {
//        return userService.setUserProfile(tblUser);
//    }
//
//    @PostMapping("/api/user/setWalletAddress")
//    public ResultVO setWalletAddress(@RequestBody Map<String, Object> params) {
//        return userService.setWalletAddress(params);
//    }
}
