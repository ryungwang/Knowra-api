package com.knowra.community.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.PaginationInfo;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.entity.TblComFile;
import com.knowra.common.entity.TblTag;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.common.repository.TblTagRepository;
import com.knowra.community.entity.TblCommPost;
import com.knowra.community.entity.TblCommPostTag;
import com.knowra.community.repository.TblCommMbrRepository;
import com.knowra.community.repository.TblCommPostRepository;
import com.knowra.community.repository.TblCommPostTagRepository;
import com.knowra.community.repository.TblCommRepository;
import org.modelmapper.ModelMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Transactional
public class CommunityPostService {
    private final ModelMapper modelMapper;
    private final TblCommPostRepository tblCommPostRepository;
    private final TblCommPostTagRepository tblCommPostTagRepository;
    private final TblTagRepository tblTagRepository;
    private final JwtProvider jwtProvider;

    @PersistenceContext
    private EntityManager em;

    @Value("${Globals.pageSize}")
    private int pageSize;

    @Value("${Globals.pageUnit}")
    private int pageUnit;

    public ResultVO setCommPost(Map<String, Object> params, String token) {

        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            TblCommPost tblCommPost = modelMapper.map(params, TblCommPost.class);
            tblCommPost.setUserSn(userSn);
            tblCommPost.setCreatrSn(userSn);
            tblCommPostRepository.save(tblCommPost);

            List<String> tagNms = (List<String>) params.get("tagNms");
            for (String tagNm : tagNms) {
                TblTag tblTag = tblTagRepository.findByTagNm(tagNm);
                if(tblTag != null){
                    tblTag.setUseCount(tblTag.getUseCount() + 1);
                    tblTagRepository.save(tblTag);
                    TblCommPostTag tblCommPostTag = TblCommPostTag.builder()
                            .commPostSn(tblCommPost.getCommPostSn())
                            .tagSn(tblTag.getTagSn())
                            .build();
                    tblCommPostTagRepository.save(tblCommPostTag);
                }else{
                    tblTag = tblTagRepository.save(TblTag.builder().tagNm(tagNm).useCount(1).build());
                    tblTag.setCreatrSn(userSn);
                    TblCommPostTag tblCommPostTag = TblCommPostTag.builder()
                            .commPostSn(tblCommPost.getCommPostSn())
                            .tagSn(tblTag.getTagSn())
                            .build();
                    tblCommPostTagRepository.save(tblCommPostTag);
                }
            }

            resultVO.putResult("commPostSn", tblCommPost.getCommPostSn());
            resultVO.setResultCode(ResponseCode.COMMUNITY_CREATE_SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_CREATE_SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.COMMUNITY_CREATE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_CREATE_ERROR.getMessage());
        }

        return resultVO;
    }
}
