package com.knowra.login.service;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.user.entity.*;
import com.knowra.user.entity.TblUser;
import com.knowra.user.repository.TblUserLgnHstryRepository;
import com.knowra.user.repository.TblUserRepository;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Transactional
public class LoginService {

    @Autowired
//    private JwtTokenUtil jwtTokenUtil;

    private final RedisApiService redisApiService;

    private static final Logger logger = LoggerFactory.getLogger(LoginService.class);

    private final EntityManager em;
    private final TblUserRepository tblUserRepository;
    private final TblUserLgnHstryRepository tblUserLgnHstryRepository;

    public ResultVO setRedisSession(Map<String, Object> params) {
        ResultVO resultVO = new ResultVO();

        try {
            ObjectMapper mapper = new ObjectMapper();

            if(!StringUtils.isEmpty(params.get("token"))){
                Object redisData = redisApiService.getRedis(15, params.get("token").toString());

                if (redisData != null) {
                    TblUser tblUser = mapper.readValue((String) redisData, TblUser.class);

//                    resultVO.putResult("address", tblUser.getWalletAddress());
//                    resultVO.putResult("chainId", Integer.parseInt(tblUser.getChainId()));
                    resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                }
            }

            if(resultVO.getResultCode() != ResponseCode.SUCCESS.getCode()){
                resultVO.setResultCode(ResponseCode.LOGIN_ERROR.getCode());
                resultVO.setResultMessage(ResponseCode.LOGIN_ERROR.getMessage());
            }
        }catch (Exception e){
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.LOGIN_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.LOGIN_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getEmailDuplicationCheck(Map<String, Object> params) {
        ResultVO resultVO = new ResultVO();

        try {
            QTblUser qTblUser = QTblUser.tblUser;
            JPAQueryFactory q = new JPAQueryFactory(em);

            Long result = q.select(qTblUser.count()).from(qTblUser).where(qTblUser.email.eq(params.get("email").toString())).fetchOne();

            resultVO.putResult("rs", result.intValue());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        }catch (Exception e){
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }


        return resultVO;
    }

    public ResultVO join(TblUser tblUser) {
        ResultVO resultVO = new ResultVO();

        try {
//            tblUser.setPassword(passwordEncoder.encode(request.get("password")));
            tblUser.setEmail(tblUser.getEmail());
            tblUser.setName(tblUser.getName());

            tblUserRepository.save(tblUser);

            resultVO.putResult("userSn", tblUser.getUserSn());
            resultVO.setResultCode(ResponseCode.JOIN_SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.JOIN_SUCCESS.getMessage());
        }catch (Exception e){
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }


        return resultVO;
    }

    public ResultVO login(/*LoginDTO loginDto,*/ HttpServletRequest request) {
        ResultVO resultVO = new ResultVO();

//        try {
//            HttpSession session = request.getSession(true);
//
//            TblUser tblUser;
//            QTblUser qTblUser = QTblUser.tblUser;
//            JPAQueryFactory q = new JPAQueryFactory(em);
//            BooleanBuilder builder = new BooleanBuilder();
//            builder.and(qTblUser.actvtnYn.eq("Y"));
//
//            if(loginDto.getLoginType().equals("join")){
//                tblUser = q.selectFrom(qTblUser).where(qTblUser.userSn.eq(loginDto.getUserSn())).fetchOne();
//            }else if(loginDto.getLoginType().equals("base")){
//                tblUser = q.selectFrom(qTblUser).where(qTblUser.email.eq(loginDto.getEmail())).fetchOne();
//            }else{
//                tblUser = q.selectFrom(qTblUser).where(qTblUser.walletAddress.eq(loginDto.getWalletAddress())).fetchOne();
//            }
//
//            if(tblUser == null){
//                resultVO.setResultCode(ResponseCode.NOT_USER.getCode());
//                resultVO.setResultMessage(ResponseCode.NOT_USER.getMessage());
//
//                return resultVO;
//            }else{
//                loginChk(tblUser, resultVO);
//
//                if(resultVO.getResultCode() == ResponseCode.SUCCESS.getCode()){
//                    if(loginDto.getLoginType().equals("base")){
//                        if(!tblUser.getPassword().equals(EncryptionUtil.hashWithSHA256(loginDto.getEmail(), loginDto.getPassword()))){
//                            long lgnFailNmtm = tblUser.getLgnFailNmtm() + 1;
//                            tblUserRepository.setLgnFailNmtmUpd(tblUser.getUserSn(), lgnFailNmtm);
//
//                            if(lgnFailNmtm == 5L){
//                                tblUserRepository.setSuspensionOfUseUpd(tblUser.getUserSn());
//                            }
//
//                            resultVO.setResultCode(ResponseCode.NOT_EQ_PASSWORD.getCode());
//                            resultVO.setResultMessage(ResponseCode.NOT_EQ_PASSWORD.getMessage());
//
//                            return resultVO;
//                        }
//                    }else if(loginDto.getLoginType().equals("wallet")){
//                        tblUser.setChainId(loginDto.getChainId());
//                    }
//
//                    String token = jwtTokenUtil.generateToken(tblUser.getUserSn().toString());
//
//                    ObjectMapper mapper = new ObjectMapper();
//                    mapper.registerModule(new JavaTimeModule());
//
//                    redisApiService.setRedis(15, token, mapper.writeValueAsString(tblUser), null);
//
//                    TblUserLgnHstry tblUserLgnHstry = new TblUserLgnHstry();
//                    tblUserLgnHstry.setUserSn(tblUser.getUserSn());
//                    tblUserLgnHstry.setLgnIp(IpUtils.getClientIp(request));
//                    tblUserLgnHstryRepository.save(tblUserLgnHstry);
//
//                    resultVO.putResult("user", tblUser);
//                    resultVO.putResult("token", token);
//                }
//            }
//        }catch (Exception e){
//            e.printStackTrace();
//            resultVO.setResultCode(ResponseCode.LOGIN_ERROR.getCode());
//            resultVO.setResultMessage(ResponseCode.LOGIN_ERROR.getMessage());
//        }

        return resultVO;
    }

    private void loginChk(TblUser tblUser, ResultVO resultVO){
        if(tblUser.getStatus().equals("W")){
            resultVO.setResultCode(ResponseCode.WAITING_FOR_APPROVAL.getCode());
            resultVO.setResultMessage(ResponseCode.WAITING_FOR_APPROVAL.getMessage());
        }else if(tblUser.getStatus().equals("R")){
            resultVO.setResultCode(ResponseCode.REJECT_FOR_APPROVAL.getCode());
            resultVO.setResultMessage(ResponseCode.REJECT_FOR_APPROVAL.getMessage());
        }else if(tblUser.getStatus().equals("C")){
            resultVO.setResultCode(ResponseCode.SUSPENSION_OF_USE.getCode());
            resultVO.setResultMessage(ResponseCode.SUSPENSION_OF_USE.getMessage());
        }else{
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }
    }

    public ResultVO logOut(Map<String, Object> params, HttpServletRequest request) {
        ResultVO resultVO = new ResultVO();

        try {
            if(!StringUtils.isEmpty(params.get("token"))){
                redisApiService.delRedis(15, params.get("token").toString());
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate(); // 세션 삭제
                }
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        }catch (Exception e){
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.LOGOUT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.LOGOUT_ERROR.getMessage());
        }

        return resultVO;
    }
}