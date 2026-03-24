package com.knowra.community.service;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.entity.QTblComFile;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.community.entity.TblCommunity;
import com.knowra.community.repository.TblCommunityRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import static com.knowra.admin.entity.QTblPst.tblPst;


@Service
@RequiredArgsConstructor
@Transactional
public class CommunityService {

    private static final Logger logger = LoggerFactory.getLogger(CommunityService.class);
    private final TblCommunityRepository tblCommunityRepository;
    private final TblComFileRepository tblComFileRepository;
    private final FileUtil fileUtil;

    @PersistenceContext
    private EntityManager em;

    private final RedisApiService redisApiService;

    public ResultVO setCommunity(TblCommunity tblCommunity, MultipartHttpServletRequest request) {
        ResultVO resultVO = new ResultVO();

        try {
            tblCommunityRepository.save(tblCommunity);

            if(request.getFile("logoImage") != null) {
                tblComFileRepository.save(
                        fileUtil.devFileInf(
                                request.getFile("logoImage"),
                                "/communities/" + tblCommunity.getCommunitySn() + "/logo",
                                "community_" + tblCommunity.getCommunitySn()
                        )
                );
            }

            if(request.getFile("bannerImage") != null) {
                tblComFileRepository.save(
                        fileUtil.devFileInf(
                                request.getFile("bannerImage"),
                                "/communities/" + tblCommunity.getCommunitySn() + "/banner",
                                "community_" + tblCommunity.getCommunitySn()
                        )
                );
            }

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
