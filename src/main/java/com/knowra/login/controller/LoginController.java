package com.knowra.login.controller;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResultVO;
import com.knowra.login.service.LoginService;
import com.knowra.user.entity.TblUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LoginController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private JwtProvider jwtProvider;

//    @PostMapping("/api/auth/setRedisSession")
//    public ResultVO setRedisSession(@RequestBody Map<String, Object> params) {
//        return loginService.setRedisSession(params);
//    }
//
//    @PostMapping("/api/auth/getEmailDuplicationCheck")
//    public ResultVO getEmailDuplicationCheck(@RequestBody Map<String, Object> params) {
//        return loginService.getEmailDuplicationCheck(params);
//    }
//
//    @PostMapping("/api/auth/join")
//    public ResultVO join(@RequestBody TblUser tblUser) {
//        return loginService.join(tblUser);
//    }
//
//    @PostMapping("/api/auth/login")
//    public ResultVO login(/*@RequestBody LoginDTO loginDto, */HttpServletRequest request) {
//        return loginService.login(/*loginDto,*/ request);
//    }
//
//    @PostMapping("/api/auth/logOut")
//    public ResultVO logOut(@RequestBody Map<String, Object> params, HttpServletRequest request) {
//        return loginService.logOut(params, request);
//    }
}