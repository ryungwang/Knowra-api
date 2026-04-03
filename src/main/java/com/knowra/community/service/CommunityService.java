package com.knowra.community.service;

import com.knowra.cmm.jwt.JwtProvider;
import com.knowra.cmm.model.PaginationInfo;
import com.knowra.cmm.model.ResponseCode;
import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.service.RedisApiService;
import com.knowra.cmm.util.FileUtil;
import com.knowra.common.entity.QTblComFile;
import com.knowra.common.entity.TblComFile;
import com.knowra.common.repository.TblComFileRepository;
import com.knowra.community.entity.*;
import com.knowra.community.repository.TblCommRepository;
import com.knowra.community.repository.TblCommMbrRepository;
import com.knowra.user.entity.TblUserActionLog;
import com.knowra.user.service.ActionLogService;
import com.knowra.user.service.InterestScoreService;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Transactional
public class CommunityService {

    private final TblCommRepository tblCommRepository;
    private final TblCommMbrRepository tblCommMbrRepository;
    private final TblComFileRepository tblComFileRepository;
    private final FileUtil fileUtil;
    private final RedisApiService redisApiService;
    private final JwtProvider jwtProvider;
    private final ActionLogService actionLogService;
    private final InterestScoreService interestScoreService;

    @PersistenceContext
    private EntityManager em;

    @Value("${Globals.pageSize}")
    private int pageSize;

    @Value("${Globals.pageUnit}")
    private int pageUnit;


    public ResultVO setCommunity(TblComm tblComm, MultipartHttpServletRequest request, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            if (tblCommRepository.findByCommNm(tblComm.getCommNm()) != null) {
                resultVO.setResultCode(ResponseCode.COMMUNITY_NAME_DUPLICATE.getCode());
                resultVO.setResultMessage(ResponseCode.COMMUNITY_NAME_DUPLICATE.getMessage());
                return resultVO;
            }

            tblComm.setCreatrSn(userSn);
            tblCommRepository.save(tblComm);

            String psnTblSn = "community_" + tblComm.getCommSn();

            if (request.getFile("logoImage") != null) {
                TblComFile logoFile = fileUtil.devFileInf(
                        request.getFile("logoImage"),
                        "/communities/" + tblComm.getCommSn() + "/logo",
                        psnTblSn
                );
                logoFile.setCreatrSn(userSn);
                TblComFile savedLogo = tblComFileRepository.save(logoFile);
                tblComm.setLogoFileSn(savedLogo.getAtchFileSn());
            }

            if (request.getFile("bannerImage") != null) {
                TblComFile bannerFile = fileUtil.devFileInf(
                        request.getFile("bannerImage"),
                        "/communities/" + tblComm.getCommSn() + "/banner",
                        psnTblSn
                );
                bannerFile.setCreatrSn(userSn);
                TblComFile savedBanner = tblComFileRepository.save(bannerFile);
                tblComm.setBnrFileSn(savedBanner.getAtchFileSn());
            }

            tblCommRepository.save(tblComm);

            TblCommMbr ownerMember = TblCommMbr.builder()
                    .commSn(tblComm.getCommSn())
                    .userSn(userSn)
                    .role("OWNER")
                    .joinTyp("AUTO")
                    .stat("ACTIVE")
                    .actvtnYn("Y")
                    .creatrSn(userSn)
                    .build();
            tblCommMbrRepository.save(ownerMember);

            resultVO.putResult("commSn", tblComm.getCommSn());
            resultVO.setResultCode(ResponseCode.COMMUNITY_CREATE_SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_CREATE_SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.COMMUNITY_CREATE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_CREATE_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getCommunity(String commNm, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            QTblComFile qLogoFile = new QTblComFile("logoFile");
            QTblComFile qBannerFile = new QTblComFile("bannerFile");

            TblComm tblComm = tblCommRepository.findByCommNm(commNm);
            if (tblComm == null) {
                resultVO.setResultCode(ResponseCode.COMMUNITY_NOT_FOUND.getCode());
                resultVO.setResultMessage(ResponseCode.COMMUNITY_NOT_FOUND.getMessage());
                return resultVO;
            }

            TblComFile logoFile = new JPAQueryFactory(em)
                .selectFrom(qLogoFile)
                .where(
                    qLogoFile.psnTblSn.eq(
                       Expressions.stringTemplate("CONCAT('community_', {0})", tblComm.getCommSn())
                    ).and(qLogoFile.atchFilePathNm.contains("logo")))
                .fetchOne();

            TblComFile bannerFile = new JPAQueryFactory(em)
                .selectFrom(qBannerFile)
                .where(
                    qBannerFile.psnTblSn.eq(
                        Expressions.stringTemplate("CONCAT('community_', {0})", tblComm.getCommSn())
                    ).and(qBannerFile.atchFilePathNm.contains("banner")))
                .fetchOne();

            TblCommMbr myMember = tblComm.getMembers().stream()
                    .filter(m -> m.getUserSn() == userSn)
                    .findFirst().orElse(null);

            resultVO.putResult("community", tblComm);
            resultVO.putResult("logoFile", logoFile);
            resultVO.putResult("bannerFile", bannerFile);
            resultVO.putResult("isMember", myMember != null);
            resultVO.putResult("memberStatus", myMember != null ? myMember.getStat() : null);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.COMMUNITY_SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getMyCommunityList(String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            QTblComm    qComm = QTblComm.tblComm;
            QTblCommMbr qMbr  = QTblCommMbr.tblCommMbr;

            List<com.querydsl.core.Tuple> tuples = new JPAQueryFactory(em)
                    .select(qComm, qMbr.role)
                    .from(qMbr)
                    .join(qComm).on(qMbr.commSn.eq(qComm.commSn))
                    .where(qMbr.userSn.eq(userSn)
                            .and(qMbr.stat.eq("ACTIVE"))
                            .and(qComm.actvtnYn.eq("Y")))
                    .orderBy(qMbr.frstCrtDt.asc())
                    .fetch();

            List<Map<String, Object>> communities = tuples.stream()
                    .map(t -> {
                        TblComm c = t.get(qComm);
                        Map<String, Object> map = new java.util.LinkedHashMap<>();
                        map.put("commSn",     c.getCommSn());
                        map.put("commNm",     c.getCommNm());
                        map.put("commDsplNm", c.getCommDsplNm());
                        map.put("commDesc",   c.getCommDesc());
                        map.put("ctgrSn",     c.getCtgrSn());
                        map.put("prvcyStng",  c.getPrvcyStng());
                        map.put("logoFileSn", c.getLogoFileSn());
                        map.put("bnrFileSn",  c.getBnrFileSn());
                        map.put("memberCnt",  c.getMemberCnt());
                        map.put("frstCrtDt",  c.getFrstCrtDt());
                        map.put("myRole",     t.get(qMbr.role));
                        return map;
                    })
                    .toList();

            resultVO.putResult("communities", communities);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.COMMUNITY_SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO setMember(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            TblComm tblComm = tblCommRepository.findByCommSn(
                    Long.valueOf(params.get("commSn").toString()));

            JPAQueryFactory q = new JPAQueryFactory(em);
            QTblCommMbr qTblCommMbr = QTblCommMbr.tblCommMbr;

            boolean isMember = Boolean.parseBoolean(params.get("isMember").toString());
            String memberStatus;

            Optional<TblCommMbr> existing =
                    tblCommMbrRepository.findByCommSnAndUserSn(tblComm.getCommSn(), userSn);

            if (!isMember) {
                TblCommMbr member = existing
                        .orElseThrow(() -> new RuntimeException(ResponseCode.COMMUNITY_NOT_MEMBER.getMessage()));

                if ("OWNER".equals(member.getRole())) {
                    throw new RuntimeException(ResponseCode.COMMUNITY_OWNER_CANNOT_LEAVE.getMessage());
                }

                q.update(qTblCommMbr)
                    .set(qTblCommMbr.stat, "WITHDRAWN")
                    .where(
                        qTblCommMbr.commSn.eq(tblComm.getCommSn())
                        .and(qTblCommMbr.userSn.eq(userSn))
                    ).execute();
                tblComm.setMemberCnt(Math.max(0, tblComm.getMemberCnt() - 1));
                tblCommRepository.save(tblComm);
                interestScoreService.update(userSn, TblUserActionLog.TARGET_COMM, tblComm.getCommSn(), -8);
                memberStatus = "WITHDRAWN";
            } else {
                String privacy = tblComm.getPrvcyStng();
                if ("public".equals(privacy) || "anonymous".equals(privacy)) {
                    memberStatus = "ACTIVE";
                } else {
                    memberStatus = "PENDING";
                }
                String joinTyp = "ACTIVE".equals(memberStatus) ? "AUTO" : "APPLY";

                if (existing.isPresent()) {
                    String currentStat = existing.get().getStat();
                    if ("ACTIVE".equals(currentStat) || "PENDING".equals(currentStat)) {
                        throw new RuntimeException(ResponseCode.COMMUNITY_ALREADY_JOINED.getMessage());
                    }
                    if ("BANNED".equals(currentStat)) {
                        throw new RuntimeException(ResponseCode.COMMUNITY_BANNED_USER.getMessage());
                    }
                    // WITHDRAWN → 기존 레코드 재활성화
                    q.update(qTblCommMbr)
                        .set(qTblCommMbr.stat, memberStatus)
                        .set(qTblCommMbr.joinTyp, joinTyp)
                        .where(
                            qTblCommMbr.commSn.eq(tblComm.getCommSn())
                            .and(qTblCommMbr.userSn.eq(userSn))
                        ).execute();
                } else {
                    TblCommMbr tblCommMbr = TblCommMbr.builder()
                            .commSn(tblComm.getCommSn())
                            .userSn(userSn)
                            .stat(memberStatus)
                            .joinTyp(joinTyp)
                            .actvtnYn("Y")
                            .creatrSn(userSn)
                            .build();
                    tblCommMbrRepository.save(tblCommMbr);
                }
                if ("ACTIVE".equals(memberStatus)) {
                    tblComm.setMemberCnt(tblComm.getMemberCnt() + 1);
                    tblCommRepository.save(tblComm);
                    actionLogService.log(userSn, TblUserActionLog.TARGET_COMM, tblComm.getCommSn(), TblUserActionLog.ACTION_JOIN);
                    interestScoreService.update(userSn, TblUserActionLog.TARGET_COMM, tblComm.getCommSn(), 8);
                }
            }

            resultVO.putResult("memberStatus", memberStatus);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (RuntimeException e) {
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_MEMBER_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getCommunityList(Map<String, Object> params, String token) {
        ResultVO resultVO = new ResultVO();
        PaginationInfo paginationInfo = new PaginationInfo();

        try {
            long userSn = jwtProvider.extractUserSn(token.replace("Bearer ", ""));

            QTblComm qTblComm = QTblComm.tblComm;


//            QTblUseCoin qTblUseCoin = QTblUseCoin.tblUseCoin;
//            QTblWalletAddress qTblWalletAddress = QTblWalletAddress.tblWalletAddress;
//            QTblUser qTblUser = QTblUser.tblUser;
//            QTblUser qTblUser2 = new QTblUser("qTblUser2");
//
//            JPAQueryFactory q = new JPAQueryFactory(em);
//            BooleanBuilder builder = new BooleanBuilder();
//            builder.and(
//                qTblComm.actvtnYn.eq("Y").and(qTblComm.stat.eq("Y")).and(qTblComm.prvcyStng.eq("public").or(qTblComm.prvcyStng.eq("anonymous")))
//            );
//
//            if (!StringUtils.isEmpty(params.get("pageIndex"))) {
//                paginationInfo.setCurrentPageNo(Integer.parseInt(params.get("pageIndex").toString()));
//            }
//
//            if (!StringUtils.isEmpty(params.get("pageSize"))) {
//                paginationInfo.setPageSize(Integer.parseInt(params.get("pageSize").toString()));
//            }else{
//                paginationInfo.setPageSize(pageSize);
//            }
//
//            if (!StringUtils.isEmpty(params.get("pageUnit"))) {
//                paginationInfo.setRecordCountPerPage(Integer.parseInt(params.get("pageUnit").toString()));
//            }else{
//                paginationInfo.setRecordCountPerPage(pageUnit);
//            }
//
//
//            if (!StringUtils.isEmpty(params.get("network"))) {
//                builder.and(qTblDwHistory.network.eq(params.get("network").toString()));
//            }
//
//            if (!StringUtils.isEmpty(params.get("coinName"))) {
//                builder.and(qTblDwHistory.coinName.contains(params.get("coinName").toString()));
//            }
//
//            if (!StringUtils.isEmpty(params.get("adminAddress"))) {
//                builder.and(qTblDwHistory.adminAddress.eq(params.get("adminAddress").toString()));
//            }
//
//            if(!StringUtils.isEmpty(params.get("searchVal"))){
//                if (!StringUtils.isEmpty(params.get("searchType"))) {
//                    if(params.get("searchType").equals("email")){
//                        builder.and(qTblUser.email.contains(params.get("searchVal").toString()));
//                    }else if(params.get("searchType").equals("name")){
//                        builder.and(qTblUser.name.contains(params.get("searchVal").toString()));
//                    }
//                }else{
//                    builder.and(
//                                    qTblUser.email.contains(params.get("searchVal").toString()))
//                            .or(qTblUser.name.contains(params.get("searchVal").toString())
//                            );
//                }
//            }
//
//            JPAQuery<DwHistoryDTO> query =  q
//                    .select(
//                            Projections.constructor(
//                                    DwHistoryDTO.class,
//                                    qTblDwHistory,
//                                    qTblUseCoin,
//                                    qTblUser,
//                                    qTblUser2
//                            )
//                    ).from(qTblDwHistory)
//                    .join(qTblUser)
//                    .on(qTblDwHistory.requestUserSn.eq(qTblUser.userSn))
//                    .join(qTblUseCoin)
//                    .on(qTblUseCoin.coinName.eq(qTblDwHistory.coinName))
//                    .leftJoin(qTblUser2)
//                    .on(qTblDwHistory.confirmUserSn.eq(qTblUser2.userSn))
//                    .offset(paginationInfo.getFirstRecordIndex())
//                    .limit(paginationInfo.getRecordCountPerPage());
//
//
//            JPAQuery<Long> cntQuery =  q.select(qTblDwHistory.count())
//                    .from(qTblDwHistory)
//                    .join(qTblUser)
//                    .on(qTblDwHistory.requestUserSn.eq(qTblUser.userSn))
//                    .join(qTblUseCoin)
//                    .on(qTblUseCoin.coinName.eq(qTblDwHistory.coinName));
//
//            List<DwHistoryDTO> tblDwHistory = query
//                    .where(builder)
//                    .orderBy(qTblDwHistory.frstCrtDt.desc())
//                    .fetch();
//
//            Long totCnt = cntQuery
//                    .where(builder)
//                    .fetchOne();
//            if(totCnt == null) totCnt = 0L;
//            paginationInfo.setTotalRecordCount(totCnt.intValue());
//
//            resultVO.putResult("tblDwHistory", tblDwHistory);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            resultVO.setResultCode(ResponseCode.COMMUNITY_SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_SELECT_ERROR.getMessage());
        }

        return resultVO;
    }
}
