package com.knowra.community.service;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.entity.QTblComFile;
import com.knowra.common.entity.TblComFile;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.community.entity.*;
import com.knowra.community.repository.TblCommunitiesRepository;
import com.knowra.community.repository.TblCommunityMemberRepository;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;


@Service
@RequiredArgsConstructor
@Transactional
public class CommunityService {

    private final TblCommunitiesRepository tblCommunitiesRepository;
    private final TblCommunityMemberRepository tblCommunityMemberRepository;
    private final TblComFileRepository tblComFileRepository;
    private final FileUtil fileUtil;
    private final RedisApiService redisApiService;

    @PersistenceContext
    private EntityManager em;

    public ResultVO setCommunity(TblCommunities tblCommunities, MultipartHttpServletRequest request) {
        ResultVO resultVO = new ResultVO();

        try {
            tblCommunitiesRepository.save(tblCommunities);

            if(request.getFile("logoImage") != null) {
                tblComFileRepository.save(
                        fileUtil.devFileInf(
                                request.getFile("logoImage"),
                                "/communities/" + tblCommunities.getCommunitySn() + "/logo",
                                "community_" + tblCommunities.getCommunitySn()
                        )
                );
            }

            if(request.getFile("bannerImage") != null) {
                tblComFileRepository.save(
                        fileUtil.devFileInf(
                                request.getFile("bannerImage"),
                                "/communities/" + tblCommunities.getCommunitySn() + "/banner",
                                "community_" + tblCommunities.getCommunitySn()
                        )
                );
            }

            resultVO.putResult("communitySn", tblCommunities.getCommunitySn());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e){
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getCommunity(String communityNm) {
        ResultVO resultVO = new ResultVO();

        try {
            TblCommunities tblCommunities = tblCommunitiesRepository.findByCommunityNm(communityNm);
            if(tblCommunities == null) throw new IllegalStateException("Community not found: " + communityNm);

//            QTblCommunities qTblCommunities = QTblCommunities.tblCommunities;
//            QTblCommunityMember qTblCommunityMember = QTblCommunityMember.tblCommunityMember;
            QTblComFile qLogoFile = new QTblComFile("logoFile");
            QTblComFile qBannerFile = new QTblComFile("bannerFile");

//            JPQLQuery<Long> communityMemberCnt = JPAExpressions
//                    .select(qTblCommunityMember.count())
//                    .from(qTblCommunityMember)
//                    .where(qTblCommunityMember.communitySn.eq(qTblCommunities.communitySn));
//
//            CommunitytDTO result = new JPAQueryFactory(em)
//                    .select(
//                            Projections.constructor(
//                                    CommunitytDTO.class,
//                                    qTblCommunities,
//                                    communityMemberCnt,
//                                    logoFile,
//                                    bannerFile
//                            )
//                    )
//                    .from(qTblCommunities)
//                    .leftJoin(logoFile).on(
//                            logoFile.psnTblSn.eq(Expressions.stringTemplate("CONCAT('community_', {0})", qTblCommunities.communitySn))
//                            .and(logoFile.atchFilePathNm.contains("logo"))
//                    )
//                    .leftJoin(bannerFile).on(
//                            bannerFile.psnTblSn.eq(Expressions.stringTemplate("CONCAT('community_', {0})", qTblCommunities.communitySn))
//                            .and(bannerFile.atchFilePathNm.contains("banner"))
//                    )
//                    .where(qTblCommunities.communityNm.eq(communityNm))
//                    .fetchOne();

//            if (result == null) throw new IllegalStateException("Community not found: " + communityNm);

            TblComFile logoFile = new JPAQueryFactory(em)
                    .selectFrom(qLogoFile)
                    .where(
                            qLogoFile.psnTblSn.eq(
                                            Expressions.stringTemplate("CONCAT('community_', {0})", tblCommunities.getCommunitySn())
                                    )
                                    .and(qLogoFile.atchFilePathNm.contains("logo"))).fetchOne();

            TblComFile bannerFile = new JPAQueryFactory(em)
                    .selectFrom(qBannerFile)
                    .where(
                            qBannerFile.psnTblSn.eq(
                                Expressions.stringTemplate("CONCAT('community_', {0})", tblCommunities.getCommunitySn())
                            )
                            .and(qBannerFile.atchFilePathNm.contains("banner"))).fetchOne();

            resultVO.putResult("community", tblCommunities);
            resultVO.putResult("communityMembers", tblCommunityMemberRepository.findAllByCommunitySn(tblCommunities.getCommunitySn()));
            resultVO.putResult("logoFile", logoFile);
            resultVO.putResult("bannerFile", bannerFile);

            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        }catch (Exception e){
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }
}
