package com.knowra.user.controller;

import com.knowra.user.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin("*")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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
