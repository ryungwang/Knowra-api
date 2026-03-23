package com.knowra.community.service;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.community.entity.TblCommunity;
import com.knowra.community.repository.TblCommunityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartRequest;


@Service
@RequiredArgsConstructor
@Transactional
public class CommunityService {

    private static final Logger logger = LoggerFactory.getLogger(CommunityService.class);
    private final TblCommunityRepository tblCommunityRepository;

    @PersistenceContext
    private EntityManager em;

    private final RedisApiService redisApiService;

    public ResultVO setCommunity(TblCommunity tblCommunity, MultipartRequest request) {
        ResultVO resultVO = new ResultVO();

        try {
            System.out.println(request.getFiles("bannerImage"));
//            tblCommunityRepository.save(tblCommunity);


//            TblUserBalance tblUserBalance = new TblUserBalance();
//            tblUserBalance.setUserSn(tblUser.getUserSn());
//            tblUserBalance.setCreatrSn(tblUser.getUserSn());
//            tblUserBalanceRepository.save(tblUserBalance);

//            resultVO.putResult("userSn", tblUser.getUserSn());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e){
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }


        return resultVO;
    }
}
