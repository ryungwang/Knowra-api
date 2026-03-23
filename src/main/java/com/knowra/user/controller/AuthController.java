package com.knowra.user.controller;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.model.ResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;

    @PostMapping("/login")
    public ResultVO login(@RequestBody Map<String, String> request) {
        ResultVO resultVO = new ResultVO();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.get("email"),
                            request.get("password")
                    )
            );

            String accessToken = jwtProvider.generateAccessToken(request.get("email"));
            String refreshToken = jwtProvider.generateRefreshToken(request.get("email"));

            resultVO.putResult("accessToken", accessToken);
            resultVO.putResult("refreshToken", refreshToken);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (BadCredentialsException e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    @PostMapping("/refresh")
    public ResultVO refresh(@RequestBody Map<String, String> request) {
        ResultVO resultVO = new ResultVO();
        try {
            String refreshToken = request.get("refreshToken");
            if (!jwtProvider.isValid(refreshToken)) {
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("유효하지 않은 토큰입니다.");
                return resultVO;
            }

            String email = jwtProvider.extractEmail(refreshToken);
            String newAccessToken = jwtProvider.generateAccessToken(email);

            resultVO.putResult("accessToken", newAccessToken);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }
}
