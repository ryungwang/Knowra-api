package com.knowra.user.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.common.util.ComUtil;
import com.knowra.user.service.AuthService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Map;

/**
 * 인증 API 컨트롤러
 * <p>로그인, 로그아웃, 토큰 갱신, 회원가입 엔드포인트를 제공한다.</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 비밀번호 BCrypt 인코딩 (개발용)
     *
     * @return 인코딩된 비밀번호 문자열
     */
    @GetMapping("/encode")
    public String encode() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.encode("1111");
    }

    /**
     * 로그인
     * <p>아이디·비밀번호를 검증하고 Access/Refresh 토큰을 발급한다.</p>
     *
     * @param params {@code loginId}, {@code password} 포함
     * @return Access Token, Refresh Token
     */
    @PostMapping("/login")
    public ResultVO login(@RequestBody Map<String, String> params, HttpServletRequest request) {
        return authService.login(params, ComUtil.extractClientIp(request));
    }

    @PostMapping("/snsLogin")
    public ResultVO snsLogin(@RequestBody Map<String, String> params, HttpServletRequest request) {
        return authService.snsLogin(params, ComUtil.extractClientIp(request));
    }

    /**
     * 로그아웃
     * <p>Authorization 헤더의 토큰을 무효화하고 Redis 세션을 삭제한다.</p>
     *
     * @param request Authorization 헤더에 Bearer 토큰 포함
     * @return 처리 결과
     */
    @PostMapping("/logout")
    public ResultVO logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return authService.logout(token);
    }

    /**
     * Access Token 갱신
     * <p>Refresh Token을 검증하고 새 Access Token을 발급한다.</p>
     *
     * @param request {@code refreshToken} 포함
     * @return 새 Access Token
     */
    @PostMapping("/refresh")
    public ResultVO refresh(@RequestBody Map<String, String> request) {
        return authService.refresh(request);
    }

    /**
     * 회원가입
     *
     * @param params 회원 정보 (아이디, 비밀번호, 이름 등)
     * @return 처리 결과
     */
    @PostMapping("/join")
    public ResultVO join(@RequestParam Map<String, Object> params, @RequestParam(required = false) MultipartFile profileImage, HttpServletRequest request) {
        return authService.join(params, ComUtil.extractClientIp(request), profileImage);
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
