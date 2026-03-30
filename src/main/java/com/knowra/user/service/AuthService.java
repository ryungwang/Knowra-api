package com.knowra.user.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.user.entity.TblUser;
import com.knowra.user.entity.TblUserLgnHstry;
import com.knowra.user.repository.TblUserLgnHstryRepository;
import com.knowra.user.repository.TblUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final UserService userService;
    private final TblUserRepository tblUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisApiService redisApiService;
    private final TblUserLgnHstryRepository tblUserLgnHstryRepository;

    private static final int REDIS_DB = 15;

    public ResultVO login(Map<String, String> params, String clientIp) {
        ResultVO resultVO = new ResultVO();
        try {
            String loginId = params.get("loginId");
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginId, params.get("password"))
            );

            long userSn = userService.getUserSn(loginId);
            String accessToken = jwtProvider.generateAccessToken(userSn, loginId);
            String refreshToken = jwtProvider.generateRefreshToken(userSn, loginId);

            resultVO.putResult("accessToken", accessToken);
            resultVO.putResult("refreshToken", refreshToken);

            TblUserLgnHstry tblUserLgnHstry = new TblUserLgnHstry();
            tblUserLgnHstry.setUserSn(userSn);
            tblUserLgnHstry.setLgnIp(clientIp);
            tblUserLgnHstryRepository.save(tblUserLgnHstry);

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (BadCredentialsException e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    public ResultVO logout(String token) {
        ResultVO resultVO = new ResultVO();
        try {
            if (!StringUtils.hasText(token) || !token.startsWith("Bearer ")) {
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("유효하지 않은 토큰입니다.");
                return resultVO;
            }
            String accessToken = token.substring(7);

            if (!jwtProvider.isValid(accessToken)) {
                resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
                resultVO.setResultMessage("만료되었거나 유효하지 않은 토큰입니다.");
                return resultVO;
            }

            long ttl = jwtProvider.getRemainingTtlSeconds(accessToken);
            redisApiService.addToBlocklist(REDIS_DB, accessToken, ttl);

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
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

            String loginId = jwtProvider.extractLoginId(refreshToken);
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

    public ResultVO join(Map<String, Object> params, String clientIp) {
        ResultVO resultVO = new ResultVO();

        try {
            String loginId = params.get("loginId").toString();
            String rawPassword = params.get("password").toString();

            TblUser tblUser = new TblUser();
            tblUser.setEmail(params.get("email").toString());
            tblUser.setLoginId(loginId);
            tblUser.setPassword(passwordEncoder.encode(rawPassword));
            tblUser.setName(params.get("name").toString());
            tblUserRepository.save(tblUser);

            resultVO = login(Map.of("loginId", loginId, "password", rawPassword), clientIp);
        } catch (Exception e) {
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }


}
