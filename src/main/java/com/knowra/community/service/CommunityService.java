package com.knowra.community.service;

import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.entity.TblComFile;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.community.entity.*;
import com.knowra.community.repository.TblCommunitiesRepository;
import com.knowra.community.repository.TblCommunityMemberRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;
import java.util.Map;


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

    public ResultVO setCommunity(TblCommunities tblCommunities, MultipartHttpServletRequest request, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = 1L; // TODO: 레디스에서 추출
            tblCommunities.setCreatrSn(userSn);
            tblCommunitiesRepository.save(tblCommunities);

            String psnTblSn = "community_" + tblCommunities.getCommSn();

            if (request.getFile("logoImage") != null) {
                TblComFile logoFile = fileUtil.devFileInf(
                        request.getFile("logoImage"),
                        "/communities/" + tblCommunities.getCommSn() + "/logo",
                        psnTblSn
                );
                logoFile.setCreatrSn(userSn);
                TblComFile savedLogo = tblComFileRepository.save(logoFile);
                tblCommunities.setLogoFileSn(savedLogo.getAtchFileSn());
            }

            if (request.getFile("bannerImage") != null) {
                TblComFile bannerFile = fileUtil.devFileInf(
                        request.getFile("bannerImage"),
                        "/communities/" + tblCommunities.getCommSn() + "/banner",
                        psnTblSn
                );
                bannerFile.setCreatrSn(userSn);
                TblComFile savedBanner = tblComFileRepository.save(bannerFile);
                tblCommunities.setBnrFileSn(savedBanner.getAtchFileSn());
            }

            tblCommunitiesRepository.save(tblCommunities);

            resultVO.putResult("commSn", tblCommunities.getCommSn());
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SAVE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SAVE_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getCommunity(String commNm, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = 1L; // TODO: 레디스에서 추출

            TblCommunities tblCommunities = tblCommunitiesRepository.findByCommNm(commNm);
            if (tblCommunities == null) throw new IllegalStateException("Community not found: " + commNm);

            TblCommunityMember myMember = tblCommunities.getMembers().stream()
                    .filter(m -> m.getUserSn() == userSn)
                    .findFirst().orElse(null);

            resultVO.putResult("community", tblCommunities);
            resultVO.putResult("isMember", myMember != null);
            resultVO.putResult("memberStatus", myMember != null ? myMember.getStat() : null);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getMyCommunityList(String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = 1L; // TODO: 레디스에서 추출

            List<TblCommunities> communities = tblCommunitiesRepository.findAllByMemberUserSn(userSn);

            resultVO.putResult("communities", communities);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO setMember(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = 1L; // TODO: 레디스에서 추출

            TblCommunities tblCommunities = tblCommunitiesRepository.findByCommSn(
                    Long.valueOf(params.get("communitySn").toString()));

            JPAQueryFactory q = new JPAQueryFactory(em);
            QTblCommunityMember qTblCommunityMember = QTblCommunityMember.tblCommunityMember;

            boolean isMember = Boolean.parseBoolean(params.get("isMember").toString());
            String memberStatus;

            if (!isMember) {
                /** 탈퇴 */
                q.update(qTblCommunityMember)
                    .set(qTblCommunityMember.stat, "WITHDRAWN")
                    .where(
                        qTblCommunityMember.commSn.eq(tblCommunities.getCommSn())
                        .and(qTblCommunityMember.userSn.eq(userSn))
                    ).execute();
                memberStatus = "WITHDRAWN";
            } else {
                /** 가입 - prvcyStng에 따라 분기 */
                String privacy = tblCommunities.getPrvcyStng();
                if ("public".equals(privacy) || "anonymous".equals(privacy)) {
                    memberStatus = "ACTIVE";
                } else {
                    memberStatus = "PENDING";
                }
                String joinTyp = "ACTIVE".equals(memberStatus) ? "AUTO" : "APPLY";

                TblCommunityMember tblCommunityMember = TblCommunityMember.builder()
                        .commSn(tblCommunities.getCommSn())
                        .userSn(userSn)
                        .stat(memberStatus)
                        .joinTyp(joinTyp)
                        .actvtnYn("Y")
                        .creatrSn(userSn)
                        .build();
                tblCommunityMemberRepository.save(tblCommunityMember);
            }

            resultVO.putResult("memberStatus", memberStatus);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.SELECT_ERROR.getMessage());
        }

        return resultVO;
    }
}
