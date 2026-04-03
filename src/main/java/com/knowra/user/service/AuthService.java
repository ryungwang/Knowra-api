package com.knowra.user.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.common.util.ComUtil;
import com.knowra.user.entity.TblUser;
import com.knowra.user.entity.TblUserLgnHstry;
import com.knowra.user.entity.TblUserStng;
import com.knowra.user.repository.TblUserLgnHstryRepository;
import com.knowra.user.repository.TblUserRepository;
import com.knowra.user.repository.TblUserStngRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RestTemplate restTemplate = new RestTemplate();

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final UserService userService;
    private final TblUserRepository tblUserRepository;
    private final TblUserStngRepository tblUserStngRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisApiService redisApiService;
    private final TblUserLgnHstryRepository tblUserLgnHstryRepository;
    private final TblComFileRepository tblComFileRepository;
    private final FileUtil fileUtil;

    private static final int REDIS_DB = 15;

    public ResultVO login(Map<String, String> params, String clientIp) {
        ResultVO resultVO = new ResultVO();
        try {
            String loginId = params.get("loginId");

            if(!params.get("snsYn").equals("Y")){
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(loginId, params.get("password"))
                );
            }

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

    public ResultVO snsLogin(Map<String, String> params, String clientIp) {
        ResultVO resultVO = new ResultVO();
        try {
            String url = "https://www.googleapis.com/oauth2/v1/userinfo?access_token=" + ComUtil.getStrValue(params.get("accessToken"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> userInfo = response.getBody();

            if (userInfo == null) {
                throw new RuntimeException("구글 사용자 정보 조회 실패");
            }

            String googleId = (String) userInfo.get("id");
            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");
            Boolean verifiedEmail = (Boolean) userInfo.get("verified_email");

            if (email == null || email.isBlank()) {
                throw new RuntimeException("이메일 정보가 없습니다.");
            }

            if (Boolean.FALSE.equals(verifiedEmail)) {
                throw new RuntimeException("검증되지 않은 이메일입니다.");
            }

            Optional<TblUser> tblUser = tblUserRepository.findByLoginId(email);

            if(tblUser.isEmpty()){
                resultVO.putResult("needsProfile", true);
                resultVO.putResult("googleInfo", userInfo);
            }else{
                String accessToken = jwtProvider.generateAccessToken(tblUser.get().getUserSn(), email);
                String refreshToken = jwtProvider.generateRefreshToken(tblUser.get().getUserSn(), email);

                resultVO.putResult("accessToken", accessToken);
                resultVO.putResult("refreshToken", refreshToken);

                TblUserLgnHstry tblUserLgnHstry = new TblUserLgnHstry();
                tblUserLgnHstry.setUserSn(tblUser.get().getUserSn());
                tblUserLgnHstry.setLgnIp(clientIp);
                tblUserLgnHstryRepository.save(tblUserLgnHstry);
            }


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

    public ResultVO join(Map<String, Object> params, String clientIp, MultipartFile profileImage) {
        ResultVO resultVO = new ResultVO();

        try {
            String loginId = ComUtil.getStrValue(params.get("loginId"));
            String rawPassword = ComUtil.getStrValue(params.get("password"));
            String snsYn = ComUtil.getStrValue(params.get("snsYn"));

            TblUser tblUser = new TblUser();
            tblUser.setEmail(ComUtil.getStrValue(params.get("email")));
            tblUser.setPhone(ComUtil.getStrValue(params.get("phone")));
            tblUser.setLoginId(loginId);
            tblUser.setPassword(passwordEncoder.encode(rawPassword));
            tblUser.setName(ComUtil.getStrValue(params.get("name")));
            tblUser.setNickName(ComUtil.getStrValue(params.get("nickName")));
            tblUser.setBio(ComUtil.getStrValue(params.get("bio")));
            tblUser.setInterest(ComUtil.getStrValue(params.get("interest")));

            if(snsYn.equals("Y")){
                tblUser.setSnsYn(ComUtil.getStrValue(params.get("snsYn")));
                tblUser.setSnsId(ComUtil.getStrValue(params.get("snsId")));
                tblUser.setSnsName(ComUtil.getStrValue(params.get("snsName")));
                tblUser.setPassword("SNS 연동");
            }
            tblUserRepository.save(tblUser);

            UserService.profileImageSave(profileImage, tblUser, fileUtil, tblComFileRepository, tblUserRepository);


            TblUserStng tblUserStng = new TblUserStng();
            tblUserStng.setUserSn(tblUser.getUserSn());
            tblUserStng.setCreatrSn(tblUser.getUserSn());
            tblUserStngRepository.save(tblUserStng);

            resultVO = login(Map.of("loginId", loginId, "password", rawPassword, "snsYn", snsYn), clientIp);
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }
        return resultVO;
    }
}
