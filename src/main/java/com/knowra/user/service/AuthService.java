package com.knowra.user.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.user.repository.TblUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final UserService userService;

    public ResultVO login(Map<String, String> request) {
        ResultVO resultVO = new ResultVO();
        try {
            String loginId = request.get("loginId");
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginId, request.get("password"))
            );

            long userSn = userService.getUserSn(loginId);
            String accessToken = jwtProvider.generateAccessToken(userSn, loginId);
            String refreshToken = jwtProvider.generateRefreshToken(userSn, loginId);

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

    public ResultVO refresh(Map<String, String> request) {
        ResultVO resultVO = new ResultVO();
        try {
            String refreshToken = request.get("refreshToken");
            if (!jwtProvider.isValid(refreshToken)) {
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("유효하지 않은 토큰입니다.");
                return resultVO;
            }

            String loginId = jwtProvider.extractEmail(refreshToken);
            long userSn = jwtProvider.extractUserSn(refreshToken);
            String newAccessToken = jwtProvider.generateAccessToken(userSn, loginId);

            resultVO.putResult("accessToken", newAccessToken);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }
}
