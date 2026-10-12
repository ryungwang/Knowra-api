package com.knowra.user.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.common.util.ComUtil;
import com.knowra.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 인증 API — 로그인, 로그아웃, 토큰 갱신, 회원가입.
 *
 * <p>리프레시 토큰은 응답 본문이 아니라 <b>HttpOnly 쿠키</b>({@code /api/auth} 경로)로
 * 오간다. 프론트는 액세스 토큰만 메모리에 들고, 갱신·로그아웃은 {@code credentials:'include'}
 * 로 호출한다.
 *
 * <p>예전에 있던 {@code GET /encode}(고정 문자열의 BCrypt 를 돌려주던 개발용)는 지웠다 —
 * 공개 저장소·공개 엔드포인트에 둘 것이 아니다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResultVO login(@RequestBody Map<String, String> params,
                          HttpServletRequest request, HttpServletResponse response) {
        return authService.login(params, ComUtil.extractClientIp(request), response);
    }

    @PostMapping("/snsLogin")
    public ResultVO snsLogin(@RequestBody Map<String, String> params,
                             HttpServletRequest request, HttpServletResponse response) {
        return authService.snsLogin(params, ComUtil.extractClientIp(request), response);
    }

    /** Authorization 헤더의 액세스 토큰과 쿠키의 리프레시 토큰을 폐기하고 쿠키를 지운다. */
    @PostMapping("/logout")
    public ResultVO logout(HttpServletRequest request, HttpServletResponse response) {
        return authService.logout(request.getHeader("Authorization"), request, response);
    }

    /** 쿠키의 리프레시 토큰으로 새 액세스 토큰을 발급하고 리프레시 토큰도 회전한다. */
    @PostMapping("/refresh")
    public ResultVO refresh(HttpServletRequest request, HttpServletResponse response) {
        return authService.refresh(request, response);
    }

    @PostMapping("/join")
    public ResultVO join(@RequestParam Map<String, Object> params,
                         @RequestParam(required = false) MultipartFile profileImage,
                         HttpServletRequest request, HttpServletResponse response) {
        return authService.join(params, ComUtil.extractClientIp(request), profileImage, response);
    }
}
