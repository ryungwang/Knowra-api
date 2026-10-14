package com.knowra.user.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.common.util.ComUtil;
import com.knowra.user.service.AuthService;
import com.knowra.user.sns.SnsAuthService;
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
    private final SnsAuthService snsAuthService;

    @PostMapping("/login")
    public ResultVO login(@RequestBody Map<String, String> params,
                          HttpServletRequest request, HttpServletResponse response) {
        return authService.login(params, ComUtil.extractClientIp(request), response);
    }

    /** 2단계 인증 — body: ticket(로그인이 준 확인표), code(인증 앱 6자리 또는 복구 코드) */
    @PostMapping("/login/2fa")
    public ResultVO loginTwoFactor(@RequestBody Map<String, String> params,
                                   HttpServletRequest request, HttpServletResponse response) {
        return authService.loginTwoFactor(params, ComUtil.extractClientIp(request), response);
    }

    @PostMapping("/snsLogin")
    public ResultVO snsLogin(@RequestBody Map<String, String> params,
                             HttpServletRequest request, HttpServletResponse response) {
        return authService.snsLogin(params, ComUtil.extractClientIp(request), response);
    }

    /** Authorization 헤더의 액세스 토큰과 쿠키의 리프레시 토큰을 폐기하고 쿠키를 지운다. */
    /** 설정된 소셜 제공자와 인가 URL — 프론트가 버튼을 그릴 때 쓴다. 키는 서버에만 있다. */
    @GetMapping("/sns/providers")
    public ResultVO snsProviders() {
        ResultVO r = new ResultVO();
        r.putResult("providers", snsAuthService.providers());
        r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        return r;
    }

    @PostMapping("/logout")
    public ResultVO logout(HttpServletRequest request, HttpServletResponse response) {
        return authService.logout(request.getHeader("Authorization"), request, response);
    }

    /** 쿠키의 리프레시 토큰으로 새 액세스 토큰을 발급하고 리프레시 토큰도 회전한다. */
    @PostMapping("/refresh")
    public ResultVO refresh(HttpServletRequest request, HttpServletResponse response) {
        return authService.refresh(request, response);
    }

    /** 가입 전 본인 확인 코드 발송. body: channel(email|phone), target */
    @PostMapping("/verify/send")
    public ResultVO sendVerifyCode(@RequestBody Map<String, String> params) {
        return authService.sendVerifyCode(params);
    }

    /** body: channel, target, code */
    @PostMapping("/verify/check")
    public ResultVO checkVerifyCode(@RequestBody Map<String, String> params) {
        return authService.checkVerifyCode(params);
    }

    /** body: email — 가입 여부와 무관하게 성공으로 답한다. */
    @PostMapping("/password/forgot")
    public ResultVO forgotPassword(@RequestBody Map<String, String> params) {
        return authService.forgotPassword(params);
    }

    /** body: token, newPassword */
    @PostMapping("/password/reset")
    public ResultVO resetPassword(@RequestBody Map<String, String> params) {
        return authService.resetPassword(params);
    }

    @PostMapping("/join")
    public ResultVO join(@RequestParam Map<String, Object> params,
                         @RequestParam(required = false) MultipartFile profileImage,
                         HttpServletRequest request, HttpServletResponse response) {
        return authService.join(params, ComUtil.extractClientIp(request), profileImage, response);
    }
}
