package com.knowra.user.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.user.service.AuthService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/encode")
    public String encode() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.encode("1111");
    }

    @PostMapping("/login")
    public ResultVO login(@RequestBody Map<String, String> request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public ResultVO logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return authService.logout(token);
    }

    @PostMapping("/refresh")
    public ResultVO refresh(@RequestBody Map<String, String> request) {
        return authService.refresh(request);
    }

    @PostMapping("/join")
    public ResultVO join(@RequestBody Map<String, Object> params) {
        return authService.join(params);
    }

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


    //    @PostMapping("/api/auth/logOut")
//    public ResultVO logOut(@RequestBody Map<String, Object> params, HttpServletRequest request) {
//        return loginService.logOut(params, request);
//    }
}
