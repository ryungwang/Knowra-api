package com.knowra.community.service;

import com.knowra.cmm.errlog.ErrorLog;
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
import com.knowra.community.event.CommunityMemberChangedEvent;
import com.knowra.community.repository.TblCommRepository;
import com.knowra.community.repository.TblCommMbrRepository;
import com.knowra.user.entity.TblUserActionLog;
import com.knowra.user.service.ActionLogService;
import com.knowra.user.service.InterestScoreService;
import org.springframework.context.ApplicationEventPublisher;
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
    private final com.knowra.user.repository.TblUserStngRepository userStngRepository;
    private final com.knowra.wiki.WikiService wikiService;
    private final TblComFileRepository tblComFileRepository;
    private final FileUtil fileUtil;
    private final RedisApiService redisApiService;
    private final JwtProvider jwtProvider;
    private final ActionLogService actionLogService;
    private final InterestScoreService interestScoreService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.knowra.community.modlog.ModLogService modLog;
    private final com.knowra.community.report.CommunityReportService communityReports;
    private final com.knowra.community.joinq.JoinQuestionService joinQuestions;
    private final com.knowra.community.modmail.ModmailService modmail;
    private final com.knowra.community.hold.HoldService holdService;

    @PersistenceContext
    private EntityManager em;

    @Value("${Globals.pageSize}")
    private int pageSize;

    @Value("${Globals.pageUnit}")
    private int pageUnit;


    public ResultVO setCommunity(TblComm tblComm, MultipartHttpServletRequest request, Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = me;

            if (tblCommRepository.findByCommNm(tblComm.getCommNm()) != null) {
                resultVO.setResultCode(ResponseCode.COMMUNITY_NAME_DUPLICATE.getCode());
                resultVO.setResultMessage(ResponseCode.COMMUNITY_NAME_DUPLICATE.getMessage());
                return resultVO;
            }

            // ⚠️ 폼을 엔티티에 그대로 바인딩하므로 서버가 정하는 값은 다시 박는다. 예전엔 commSn 을 실어 보내면
            // save() 가 그 커뮤니티를 덮어쓰고 보낸 사람을 OWNER 로 넣었다 — 남의 커뮤니티를 통째로 가져갈 수 있었다.
            tblComm.setCommSn(null);
            tblComm.setMembers(null);
            tblComm.setMemberCnt(0);
            tblComm.setStat("Y");
            tblComm.setActvtnYn("Y");
            tblComm.setLogoFileSn(null); tblComm.setLogoFile(null);
            tblComm.setBnrFileSn(null);  tblComm.setBnrFile(null);
            tblComm.setFrstCrtDt(java.time.LocalDateTime.now());
            tblComm.setMdfrSn(null); tblComm.setMdfcnDt(null);
            if (!java.util.Set.of("public", "restricted", "anonymous", "private").contains(tblComm.getPrvcyStng()))
                tblComm.setPrvcyStng("public");
            tblComm.setLang(com.knowra.cmm.i18n.LangDetect.commLang(tblComm.getLang()));   // 고를 수 있는 값만
            tblComm.setCreatrSn(userSn);
            tblCommRepository.save(tblComm);

            String psnTblSn = "community_" + tblComm.getCommSn();

            if (request.getFile("logoImage") != null) {
                TblComFile logoFile = fileUtil.storeImage(request.getFile("logoImage"), userSn, FileUtil.CAT_COMMUNITY_LOGO, psnTblSn);
                logoFile.setCreatrSn(userSn);
                TblComFile savedLogo = tblComFileRepository.save(logoFile);
                tblComm.setLogoFileSn(savedLogo.getAtchFileSn());
            }

            if (request.getFile("bannerImage") != null) {
                TblComFile bannerFile = fileUtil.storeImage(request.getFile("bannerImage"), userSn, FileUtil.CAT_COMMUNITY_BANNER, psnTblSn);
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
            // 만든 사람이 첫 멤버다. 전엔 0 으로 두어 목록 카드가 "0명"으로 보였다(상세는 멤버 행을 세어 1).
            tblComm.setMemberCnt(1);
            tblCommRepository.save(tblComm);

            resultVO.putResult("commSn", tblComm.getCommSn());
            resultVO.setResultCode(ResponseCode.COMMUNITY_CREATE_SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_CREATE_SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_CREATE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_CREATE_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getCommunity(String commNm, Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            // 비로그인도 볼 수 있는 화면이다 (permitAll). me 가 null 이면 멤버십·행동로그만 건너뛴다.
            Long userSn = me;

            QTblComFile qLogoFile = new QTblComFile("logoFile");
            QTblComFile qBannerFile = new QTblComFile("bannerFile");

            TblComm tblComm = tblCommRepository.findByCommNm(commNm);
            if (tblComm == null) {
                resultVO.setResultCode(ResponseCode.COMMUNITY_NOT_FOUND.getCode());
                resultVO.setResultMessage(ResponseCode.COMMUNITY_NOT_FOUND.getMessage());
                return resultVO;
            }
            // 들어왔다 = 새 글을 봤다. 사이드바 타일의 점을 지운다(멤버일 때만).
            if (userSn != null) {
                QTblCommMbr v = QTblCommMbr.tblCommMbr;
                new JPAQueryFactory(em).update(v).set(v.lastVisitAt, java.time.LocalDateTime.now())
                        .where(v.commSn.eq(tblComm.getCommSn()), v.userSn.eq(userSn)).execute();
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

            TblCommMbr myMember = userSn == null ? null : tblComm.getMembers().stream()
                    .filter(m -> m.getUserSn() == userSn)
                    .findFirst().orElse(null);

            if (userSn != null) {
                actionLogService.log(userSn, TblUserActionLog.TARGET_COMM, tblComm.getCommSn(), TblUserActionLog.ACTION_VIEW);
            }

            resultVO.putResult("community", tblComm);
            resultVO.putResult("logoFile", logoFile);
            resultVO.putResult("bannerFile", bannerFile);
            resultVO.putResult("isMember", myMember != null);
            resultVO.putResult("memberStatus", myMember != null ? myMember.getStat() : null);
            resultVO.putResult("notifOn", myMember != null && "Y".equals(myMember.getNotifYn()));
            resultVO.putResult("myRole", myMember != null && "ACTIVE".equals(myMember.getStat()) ? myMember.getRole() : null);
            // 운영진에겐 가입 대기 수를 같이 준다 — 탭에 숫자를 달아 놓치지 않게
            if (myMember != null && "ACTIVE".equals(myMember.getStat()) && isManager(myMember.getRole())) {
                long pending = tblComm.getMembers().stream().filter(x -> "PENDING".equals(x.getStat()) && "Y".equals(x.getActvtnYn())).count();
                resultVO.putResult("pendingCnt", pending);
                resultVO.putResult("reportCnt", communityReports.openCount(tblComm.getCommSn()));
                resultVO.putResult("heldCnt", holdService.count(tblComm.getCommSn()));
                resultVO.putResult("holdDays", holdService.days(tblComm.getCommSn()));
                resultVO.putResult("modmailCnt", modmail.unreadForMods(tblComm.getCommSn()));   // 안 읽은 문의
            }
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO getMyCommunityList(Long me) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = me;

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

            // 새 글 수 — 마지막 방문 뒤 남이 올린 살아 있는 글. 커뮤니티마다 한 번씩이 아니라 한 쿼리로 센다.
            com.knowra.post.entity.QTblPost qp = com.knowra.post.entity.QTblPost.tblPost;
            Map<Long, Long> newCnt = new java.util.HashMap<>();
            new JPAQueryFactory(em).select(qp.commSn, qp.count()).from(qp)
                    .join(qMbr).on(qMbr.commSn.eq(qp.commSn).and(qMbr.userSn.eq(userSn)))
                    .where(qp.actvtnYn.eq("Y"), qp.stat.eq("ACTIVE"), qp.userSn.ne(userSn), qp.frstCrtDt.after(qMbr.lastVisitAt), qMbr.stat.eq("ACTIVE"),
                            com.knowra.post.service.PostQuery.notMuted(userSn))   // 숨긴 사람·예약·검토 중 글은 「새 글」로 세지 않는다
                    .groupBy(qp.commSn).fetch()
                    .forEach(t -> newCnt.put(t.get(qp.commSn), t.get(qp.count())));

            List<Map<String, Object>> communities = tuples.stream()
                    .map(t -> {
                        TblComm c = t.get(qComm);
                        Map<String, Object> map = new java.util.LinkedHashMap<>();
                        map.put("commSn",     c.getCommSn());
                        map.put("newCnt",     newCnt.getOrDefault(c.getCommSn(), 0L));
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
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    public ResultVO setMember(Map<String, Object> params, Long me) {
        return setMember(params, me, false);
    }

    /**
     * {@code viaInvite}: 운영진이 만든 초대 링크로 들어온 경우(InviteService 만 true 로 부른다).
     * 승인제여도 바로 ACTIVE 이고, 이미 가입 신청(PENDING) 중이면 그걸 승인으로 올린다.
     * 규칙 동의와 차단(BANNED)은 초대로도 건너뛰지 않는다.
     */
    public ResultVO setMember(Map<String, Object> params, Long me, boolean viaInvite) {
        ResultVO resultVO = new ResultVO();

        try {
            long userSn = me;

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
                // 나가기는 멤버이거나 신청 중일 때만. ⚠️ 예전엔 상태를 안 봐서, 강퇴된 사람이 「나가기」를 보내면 BANNED 가
                // WITHDRAWN 으로 바뀌어 바로 다시 가입할 수 있었고(강퇴 무력화), 부를 때마다 멤버 수도 하나씩 줄었다.
                String cur = member.getStat();
                if (!"ACTIVE".equals(cur) && !"PENDING".equals(cur)) {
                    throw new RuntimeException(ResponseCode.COMMUNITY_NOT_MEMBER.getMessage());
                }

                // 일괄 update 는 @UpdateTimestamp 를 안 탄다 — 나간 때를 직접 남긴다(통계의 「나간 멤버」가 이걸 센다)
                q.update(qTblCommMbr)
                    .set(qTblCommMbr.stat, "WITHDRAWN")
                    .set(qTblCommMbr.mdfcnDt, java.time.LocalDateTime.now())
                    .where(
                        qTblCommMbr.commSn.eq(tblComm.getCommSn())
                        .and(qTblCommMbr.userSn.eq(userSn))
                    ).execute();
                if ("ACTIVE".equals(cur)) {   // 신청 중이던 사람은 인원에 든 적이 없다
                    tblComm.setMemberCnt(Math.max(0, tblComm.getMemberCnt() - 1));
                    tblCommRepository.save(tblComm);
                }
                interestScoreService.update(userSn, TblUserActionLog.TARGET_COMM, tblComm.getCommSn(), -8);
                joinQuestions.clear(tblComm.getCommSn(), userSn);   // 신청을 거둔 경우 — 답도 거둔다
                memberStatus = "WITHDRAWN";
            } else {
                // 닫힌 커뮤니티는 새 사람을 받지 않는다 — 초대 링크로 와도 마찬가지(읽기는 그대로 된다)
                if (tblComm.getClosedAt() != null) throw new RuntimeException("닫힌 커뮤니티라 지금은 참여할 수 없어요.");
                String privacy = tblComm.getPrvcyStng();
                if ("public".equals(privacy) || "anonymous".equals(privacy)) {
                    memberStatus = "ACTIVE";
                } else {
                    memberStatus = "PENDING";
                }
                if (viaInvite) memberStatus = "ACTIVE";
                String joinTyp = viaInvite ? "INVITE" : "ACTIVE".equals(memberStatus) ? "AUTO" : "APPLY";
                // 규칙이 있으면 동의해야 참여(가입 신청)할 수 있다 — 동의한 시각을 멤버 줄에 남긴다
                boolean needAgree = wikiService.hasRules(tblComm.getCommSn());
                if (needAgree && !Boolean.parseBoolean(String.valueOf(params.get("agreeRules")))) {
                    throw new RuntimeException("커뮤니티 규칙에 동의해야 참여할 수 있어요.");
                }
                java.time.LocalDateTime agreedAt = needAgree ? java.time.LocalDateTime.now() : null;
                // 승인 신청이면 가입 질문에 답이 다 있어야 한다(초대·자동 가입은 묻지 않는다)
                List<String> answers = "PENDING".equals(memberStatus) ? joinQuestions.validate(tblComm.getCommSn(), params.get("answers")) : List.of();

                if (existing.isPresent()) {
                    String currentStat = existing.get().getStat();
                    if ("ACTIVE".equals(currentStat) || ("PENDING".equals(currentStat) && !viaInvite)) {
                        throw new RuntimeException(ResponseCode.COMMUNITY_ALREADY_JOINED.getMessage());
                    }
                    if (existing.get().bannedNow(java.time.LocalDateTime.now())) {
                        java.time.LocalDateTime until = existing.get().getBanUntil();
                        String lang = com.knowra.cmm.i18n.Lang.current();
                        throw new RuntimeException(until == null ? ResponseCode.COMMUNITY_BANNED_USER.getMessage()
                                : com.knowra.cmm.i18n.Msg.tl(lang, "{0}까지 다시 참여할 수 없어요.",
                                        com.knowra.cmm.i18n.Fmt.dateTime(until, lang, userTimeZone(userSn))));
                    }
                    // 기간이 지난 강퇴는 풀린 것 — 아래에서 다른 나간 사람과 똑같이 다시 넣는다
                    // WITHDRAWN → 기존 레코드 재활성화
                    q.update(qTblCommMbr)
                        .set(qTblCommMbr.stat, memberStatus)
                        .setNull(qTblCommMbr.banUntil)
                        .setNull(qTblCommMbr.banReason)
                        .set(qTblCommMbr.joinTyp, joinTyp)
                        .set(qTblCommMbr.notifYn, "Y")
                        .set(qTblCommMbr.rulesAgreedAt, agreedAt)
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
                            .notifYn("Y")   // 막 참여한 멤버는 새 글을 보고 싶어 한다. 끄는 건 종 버튼.
                            .rulesAgreedAt(agreedAt)
                            .actvtnYn("Y")
                            .creatrSn(userSn)
                            .build();
                    tblCommMbrRepository.save(tblCommMbr);
                }
                if ("PENDING".equals(memberStatus)) joinQuestions.store(tblComm.getCommSn(), userSn, answers);
                if ("ACTIVE".equals(memberStatus)) {
                    holdService.markJoined(tblComm.getCommSn(), userSn);   // 새 멤버 글 검토는 이때부터 센다
                    tblComm.setMemberCnt(tblComm.getMemberCnt() + 1);
                    tblCommRepository.save(tblComm);
                    actionLogService.log(userSn, TblUserActionLog.TARGET_COMM, tblComm.getCommSn(), TblUserActionLog.ACTION_JOIN);
                    interestScoreService.update(userSn, TblUserActionLog.TARGET_COMM, tblComm.getCommSn(), 8);
                }
            }

            eventPublisher.publishEvent(new CommunityMemberChangedEvent(
                    userSn, tblComm.getCommSn(), isMember));

            resultVO.putResult("memberStatus", memberStatus);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (RuntimeException e) {
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_MEMBER_ERROR.getMessage());
        }

        return resultVO;
    }

    /** 새 글 알림 켜기/끄기. 활성 멤버만 바꿀 수 있다. params: commSn, notifOn */
    @Transactional
    public ResultVO setNotif(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long userSn = me;
            long commSn = Long.parseLong(params.get("commSn").toString());
            boolean on  = Boolean.parseBoolean(String.valueOf(params.get("notifOn")));
            TblCommMbr member = tblCommMbrRepository.findByCommSnAndUserSn(commSn, userSn)
                    .filter(m -> "ACTIVE".equals(m.getStat()) && "Y".equals(m.getActvtnYn()))
                    .orElse(null);
            if (member == null) {
                resultVO.setResultCode(ResponseCode.COMMUNITY_NOT_MEMBER.getCode());
                resultVO.setResultMessage(ResponseCode.COMMUNITY_NOT_MEMBER.getMessage());
                return resultVO;
            }
            member.setNotifYn(on ? "Y" : "N");
            member.setMdfrSn(userSn);
            tblCommMbrRepository.save(member);
            resultVO.putResult("notifOn", on);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.SUCCESS.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_MEMBER_ERROR.getMessage());
        }
        return resultVO;
    }

    public ResultVO getCommunityList(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        PaginationInfo paginationInfo = new PaginationInfo();

        try {
            // 비로그인도 조회 가능 (permitAll) — 이 메서드는 me 를 쓰지 않는다.

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
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_SELECT_ERROR.getMessage());
        }

        return resultVO;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 멤버 관리 — 가입 승인·거절, 강퇴, 운영진 지정, 커뮤니티 정보 수정
    // ─────────────────────────────────────────────────────────────────────

    /** 호출자의 역할(ACTIVE 멤버만). 아니면 null. */
    private String roleOf(long commSn, long userSn) {
        return tblCommMbrRepository.findByCommSnAndUserSn(commSn, userSn)
                .filter(m -> "ACTIVE".equals(m.getStat()) && "Y".equals(m.getActvtnYn()))
                .map(TblCommMbr::getRole).orElse(null);
    }
    private static boolean isManager(String role) { return "OWNER".equals(role) || "ADMIN".equals(role); }

    /**
     * 멤버 목록. params: commSn, stat(ACTIVE|PENDING|BANNED, 기본 ACTIVE).
     * ACTIVE 는 누구나(비공개 커뮤니티는 멤버만), PENDING·BANNED 는 운영진만 본다.
     */
    public ResultVO getMemberList(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long commSn = Long.parseLong(params.get("commSn").toString());
            String stat = params.get("stat") == null ? "ACTIVE" : params.get("stat").toString();
            TblComm comm = tblCommRepository.findByCommSn(commSn);
            if (comm == null) {
                resultVO.setResultCode(ResponseCode.COMMUNITY_NOT_FOUND.getCode());
                resultVO.setResultMessage(ResponseCode.COMMUNITY_NOT_FOUND.getMessage());
                return resultVO;
            }
            String myRole = me == null ? null : roleOf(commSn, me);
            boolean privateComm = "private".equals(comm.getPrvcyStng());
            if (!"ACTIVE".equals(stat) && !isManager(myRole)) {
                resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
                resultVO.setResultMessage("운영진만 볼 수 있습니다.");
                return resultVO;
            }
            if (privateComm && myRole == null) {
                resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
                resultVO.setResultMessage("멤버만 볼 수 있습니다.");
                return resultVO;
            }
            // 익명 커뮤니티의 멤버 명단은 운영진만 — 명단만으로도 "누가 여기서 활동하는지"가 드러난다
            if ("anonymous".equals(comm.getPrvcyStng()) && !isManager(myRole)) {
                resultVO.putResult("list", java.util.List.of());
                resultVO.putResult("hidden", true);
                resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
                return resultVO;
            }

            QTblCommMbr m = QTblCommMbr.tblCommMbr;
            com.knowra.user.entity.QTblUser u = com.knowra.user.entity.QTblUser.tblUser;
            QTblComFile pfp = new QTblComFile("pfp");
            List<com.querydsl.core.Tuple> rows = new JPAQueryFactory(em)
                    .select(m, u.nickName, u.name, pfp.atchFilePathNm, pfp.strgFileNm, pfp.atchFileExtnNm, pfp.fileKey)
                    .from(m).join(u).on(m.userSn.eq(u.userSn)).leftJoin(u.pfp, pfp)
                    .where(m.commSn.eq(commSn).and(m.stat.eq(stat)).and(m.actvtnYn.eq("Y")))
                    .orderBy(m.role.asc(), m.frstCrtDt.asc())
                    .fetch();
            List<Map<String, Object>> list = new java.util.ArrayList<>();
            Map<Long, List<Map<String, Object>>> answers = "PENDING".equals(stat) ? joinQuestions.answersFor(commSn) : Map.of();
            for (com.querydsl.core.Tuple t : rows) {
                TblCommMbr mb = t.get(m);
                String path = t.get(pfp.atchFilePathNm);
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("userSn",   mb.getUserSn());
                row.put("nickName", t.get(u.nickName));
                row.put("name",     t.get(u.name));
                row.put("pfpUrl",   com.knowra.cmm.storage.StorageUrls.of(path, t.get(pfp.strgFileNm), t.get(pfp.atchFileExtnNm), t.get(pfp.fileKey)));
                row.put("role",     mb.getRole());
                row.put("stat",     mb.getStat());
                if ("BANNED".equals(mb.getStat())) {   // 운영진만 보는 목록(BANNED)이다
                    row.put("banUntil",  mb.getBanUntil());
                    row.put("banReason", mb.getBanReason());
                    row.put("bannedAt",  mb.getMdfcnDt());
                }
                row.put("joinedAt", mb.getFrstCrtDt());
                if (answers.containsKey(mb.getUserSn())) row.put("answers", answers.get(mb.getUserSn()));
                list.add(row);
            }
            resultVO.putResult("list", list);
            resultVO.putResult("myRole", myRole);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_SELECT_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_SELECT_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 이 사람의 시간대(설정) — 날짜를 그 사람 기준으로 보여 줄 때 */
    private String userTimeZone(long userSn) {
        var st = userStngRepository.findByUserSn(userSn);
        return st == null ? null : st.getTimeZone();
    }

    /** 고를 수 있는 강퇴 기간(일). 없음·0 = 영구 */
    static final java.util.Set<Integer> BAN_DAYS = java.util.Set.of(1, 3, 7, 30);

    static java.time.LocalDateTime banUntil(Object days, java.time.LocalDateTime now) {
        if (days == null || String.valueOf(days).isBlank()) return null;
        int d;
        try { d = (int) Double.parseDouble(String.valueOf(days)); } catch (NumberFormatException e) { throw new IllegalArgumentException("강퇴 기간이 올바르지 않아요."); }
        if (d == 0) return null;
        if (!BAN_DAYS.contains(d)) throw new IllegalArgumentException("강퇴 기간은 1·3·7·30일 또는 영구만 고를 수 있어요.");
        return now.plusDays(d);
    }

    /** 운영 기록에 남길 한 줄 — 「7일 · 사유: 도배」 */
    static String banDetail(java.time.LocalDateTime until, String reason) {
        String period = until == null ? "영구" : until.format(java.time.format.DateTimeFormatter.ofPattern("M월 d일 H:mm")) + "까지";
        return reason == null ? period : period + " · 사유: " + reason;
    }

    /**
     * 멤버 상태 변경. params: commSn, userSn, stat(ACTIVE=승인 | REJECTED=거절 | BANNED=강퇴 | WITHDRAWN=제명해제 없이 내보내기).
     * 운영진만. OWNER 는 바꿀 수 없고, ADMIN 은 다른 ADMIN 을 건드리지 못한다(OWNER 만 가능).
     */
    @Transactional
    public ResultVO setMemberStat(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long commSn = Long.parseLong(params.get("commSn").toString());
            long target = Long.parseLong(params.get("userSn").toString());
            String stat = params.get("stat").toString();
            if (!java.util.Set.of("ACTIVE", "REJECTED", "BANNED", "WITHDRAWN").contains(stat)) {
                throw new IllegalArgumentException("지원하지 않는 상태입니다.");
            }
            String myRole = roleOf(commSn, me);
            if (!isManager(myRole)) throw new IllegalArgumentException("운영진만 할 수 있습니다.");
            TblCommMbr mb = tblCommMbrRepository.findByCommSnAndUserSn(commSn, target)
                    .orElseThrow(() -> new IllegalArgumentException("멤버가 아닙니다."));
            if ("OWNER".equals(mb.getRole())) throw new IllegalArgumentException("주인은 내보낼 수 없습니다.");
            if ("ADMIN".equals(mb.getRole()) && !"OWNER".equals(myRole)) throw new IllegalArgumentException("운영진은 주인만 바꿀 수 있습니다.");

            // 승인(ACTIVE)은 가입 신청 중(PENDING)이거나 차단 해제(BANNED)일 때만 — 스스로 나간 사람(WITHDRAWN)·거절된 사람을
            // 운영진이 동의 없이 다시 끌어넣을 수 있었다(알림까지 켜진 채로).
            if ("ACTIVE".equals(stat) && !java.util.Set.of("PENDING", "BANNED", "ACTIVE").contains(mb.getStat()))
                throw new IllegalArgumentException("스스로 나갔거나 거절된 사람은 다시 넣을 수 없어요. 초대 링크를 보내 주세요.");
            // 강퇴 해제 = BANNED → WITHDRAWN. 억지로 다시 넣지 않고, 본인이 원하면 다시 참여한다.
            boolean unban = "WITHDRAWN".equals(stat) && "BANNED".equals(mb.getStat());
            java.time.LocalDateTime banUntil = null;
            String banReason = null;
            if ("BANNED".equals(stat)) {
                banUntil = banUntil(params.get("days"), java.time.LocalDateTime.now());
                banReason = params.get("reason") == null ? null : params.get("reason").toString().strip();
                if (banReason != null && banReason.isEmpty()) banReason = null;
                if (banReason != null && banReason.length() > 300) throw new IllegalArgumentException("사유는 300자까지 쓸 수 있어요.");
            }
            TblComm comm = tblCommRepository.findByCommSn(commSn);
            String prevStat = mb.getStat();
            boolean wasActive = "ACTIVE".equals(prevStat);
            boolean nowActive = "ACTIVE".equals(stat);
            mb.setStat(stat);
            mb.setBanUntil(banUntil);
            mb.setBanReason(banReason);
            mb.setMdfrSn(me);
            if (nowActive && !wasActive) { mb.setJoinTyp("APPROVE"); mb.setNotifYn("Y"); }
            if (!nowActive) mb.setRole("MEMBER");
            tblCommMbrRepository.save(mb);
            if ("PENDING".equals(prevStat)) joinQuestions.clear(commSn, target);   // 결정이 났다 — 답은 더 쓸 데가 없다
            if (wasActive != nowActive) {
                comm.setMemberCnt(Math.max(0, comm.getMemberCnt() + (nowActive ? 1 : -1)));
                tblCommRepository.save(comm);
            }
            if (nowActive && !wasActive) {
                holdService.markJoined(commSn, target);
                actionLogService.log(target, TblUserActionLog.TARGET_COMM, commSn, TblUserActionLog.ACTION_JOIN);
                interestScoreService.update(target, TblUserActionLog.TARGET_COMM, commSn, 8);
            }
            eventPublisher.publishEvent(new CommunityMemberChangedEvent(target, commSn, nowActive));
            String logAction = switch (stat) {
                case "ACTIVE"   -> wasActive ? null : "BANNED".equals(prevStat) ? com.knowra.community.modlog.ModLogService.MEMBER_UNBAN : com.knowra.community.modlog.ModLogService.MEMBER_APPROVE;
                case "REJECTED" -> com.knowra.community.modlog.ModLogService.MEMBER_REJECT;
                case "BANNED"   -> com.knowra.community.modlog.ModLogService.MEMBER_BAN;
                default         -> unban ? com.knowra.community.modlog.ModLogService.MEMBER_UNBAN : com.knowra.community.modlog.ModLogService.MEMBER_REMOVE;
            };
            String detail = "BANNED".equals(stat) ? banDetail(banUntil, banReason) : null;
            if (logAction != null) modLog.record(commSn, me, logAction, target, null, detail);
            String typ = switch (stat) {
                case "ACTIVE" -> "COMM_APPROVED"; case "REJECTED" -> "COMM_REJECTED";
                case "BANNED" -> "COMM_BANNED";
                default -> unban ? "COMM_UNBANNED" : "COMM_REMOVED";
            };
            // 강퇴 알림엔 기간·사유를 싣는다 — 「장소|풀리는 때(ISO)|사유」. 날짜는 받는 사람의 언어·시간대로 리스너가 쓴다
            String extra = "BANNED".equals(stat)
                    ? comm.getCommDsplNm() + "|" + (banUntil == null ? "" : banUntil.toString()) + "|" + (banReason == null ? "" : banReason)
                    : comm.getCommDsplNm();
            eventPublisher.publishEvent(new com.knowra.cmm.event.NotifTriggerEvent(target, me, typ, commSn, "COMM", extra));

            resultVO.putResult("stat", stat);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_MEMBER_ERROR.getMessage());
        }
        return resultVO;
    }

    /** 운영진 지정·해제. params: commSn, userSn, role(ADMIN|MEMBER). OWNER 만. */
    @Transactional
    public ResultVO setMemberRole(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long commSn = Long.parseLong(params.get("commSn").toString());
            long target = Long.parseLong(params.get("userSn").toString());
            String role = params.get("role").toString();
            if (!"ADMIN".equals(role) && !"MEMBER".equals(role)) throw new IllegalArgumentException("지원하지 않는 역할입니다.");
            if (!"OWNER".equals(roleOf(commSn, me))) throw new IllegalArgumentException("주인만 운영진을 정할 수 있습니다.");
            TblCommMbr mb = tblCommMbrRepository.findByCommSnAndUserSn(commSn, target)
                    .filter(x -> "ACTIVE".equals(x.getStat()))
                    .orElseThrow(() -> new IllegalArgumentException("활성 멤버가 아닙니다."));
            if ("OWNER".equals(mb.getRole())) throw new IllegalArgumentException("주인의 역할은 바꿀 수 없습니다. 주인 넘기기를 써 주세요.");
            mb.setRole(role); mb.setMdfrSn(me);
            tblCommMbrRepository.save(mb);
            modLog.record(commSn, me, "ADMIN".equals(role) ? com.knowra.community.modlog.ModLogService.ROLE_ADMIN : com.knowra.community.modlog.ModLogService.ROLE_MEMBER, target, null, null);
            TblComm comm = tblCommRepository.findByCommSn(commSn);
            eventPublisher.publishEvent(new com.knowra.cmm.event.NotifTriggerEvent(target, me, "ADMIN".equals(role) ? "COMM_ADMIN" : "COMM_ADMIN_OFF", commSn, "COMM", comm.getCommDsplNm()));
            resultVO.putResult("role", role);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_MEMBER_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * 주인 넘기기. params: commSn, userSn(받을 사람), confirm(커뮤니티 이름 — 실수 방지).
     * 받을 사람은 활성 멤버면 누구나. 넘긴 사람은 운영진으로 남는다 — 그 뒤에 나가거나 탈퇴할 수 있다.
     * 주인은 커뮤니티당 하나여야 한다 — 두 번 눌러 동시에 들어와도 한쪽만 통과하게 커뮤니티 줄을 잠근다.
     */
    @Transactional
    public ResultVO transferOwner(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long commSn = Long.parseLong(params.get("commSn").toString());
            long target = Long.parseLong(params.get("userSn").toString());
            if (target == me) throw new IllegalArgumentException("이미 주인이에요.");
            TblComm comm = em.find(TblComm.class, commSn, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (comm == null || !"Y".equals(comm.getStat())) throw new IllegalArgumentException("없는 커뮤니티예요.");
            String name = StringUtils.hasText(comm.getCommDsplNm()) ? comm.getCommDsplNm() : comm.getCommNm();
            if (!name.equals(String.valueOf(params.get("confirm")).trim())) throw new IllegalArgumentException("확인을 위해 커뮤니티 이름을 정확히 입력해 주세요.");

            TblCommMbr mine = tblCommMbrRepository.findByCommSnAndUserSn(commSn, me)
                    .filter(x -> "ACTIVE".equals(x.getStat()) && "OWNER".equals(x.getRole()))
                    .orElseThrow(() -> new IllegalArgumentException("주인만 넘길 수 있어요."));
            TblCommMbr next = tblCommMbrRepository.findByCommSnAndUserSn(commSn, target)
                    .filter(x -> "ACTIVE".equals(x.getStat()))
                    .orElseThrow(() -> new IllegalArgumentException("이 커뮤니티의 멤버에게만 넘길 수 있어요."));

            mine.setRole("ADMIN"); mine.setMdfrSn(me);
            next.setRole("OWNER"); next.setMdfrSn(me);
            tblCommMbrRepository.saveAll(List.of(mine, next));
            modLog.record(commSn, me, com.knowra.community.modlog.ModLogService.OWNER_TRANSFER, target, null, null);
            eventPublisher.publishEvent(new com.knowra.cmm.event.NotifTriggerEvent(target, me, "COMM_OWNER", commSn, "COMM", name));
            resultVO.putResult("myRole", "ADMIN");
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_MEMBER_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * 커뮤니티 닫기·다시 열기 — 주인만. params: commSn, closed, confirm(닫을 때만, 커뮤니티 이름)
     *
     * <p>닫으면 읽기 전용이다: 글은 그대로 읽히고 새 글·댓글·가입이 막힌다(운영진의 안내 글·댓글은 된다).
     * 지우기 대신 이걸 두는 이유 — 운영을 그만둬도 쌓인 글은 검색으로 계속 찾아오는 기록이라서다.
     */
    @Transactional
    public ResultVO setClosed(Map<String, Object> params, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long commSn = Long.parseLong(params.get("commSn").toString());
            boolean close = Boolean.parseBoolean(String.valueOf(params.get("closed")));
            TblComm comm = em.find(TblComm.class, commSn, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (comm == null || !"Y".equals(comm.getStat())) throw new IllegalArgumentException("없는 커뮤니티예요.");
            tblCommMbrRepository.findByCommSnAndUserSn(commSn, me)
                    .filter(x -> "ACTIVE".equals(x.getStat()) && "OWNER".equals(x.getRole()))
                    .orElseThrow(() -> new IllegalArgumentException("주인만 닫거나 열 수 있어요."));
            if (close) {
                String name = StringUtils.hasText(comm.getCommDsplNm()) ? comm.getCommDsplNm() : comm.getCommNm();
                if (!name.equals(String.valueOf(params.get("confirm")).trim())) throw new IllegalArgumentException("확인을 위해 커뮤니티 이름을 정확히 입력해 주세요.");
            }
            // 이미 그 상태면 아무것도 안 한다 — 두 번 눌러 기록이 겹치거나 닫힌 시각이 밀리지 않게
            int n = close
                    ? em.createNativeQuery("UPDATE knowra_community.tbl_comm SET closed_at = now() WHERE comm_sn = ?1 AND closed_at IS NULL").setParameter(1, commSn).executeUpdate()
                    : em.createNativeQuery("UPDATE knowra_community.tbl_comm SET closed_at = NULL WHERE comm_sn = ?1 AND closed_at IS NOT NULL").setParameter(1, commSn).executeUpdate();
            if (n > 0) modLog.record(commSn, me, close ? com.knowra.community.modlog.ModLogService.COMM_CLOSE : com.knowra.community.modlog.ModLogService.COMM_REOPEN, null, null, null);
            resultVO.putResult("closed", close);
            resultVO.setResultCode(ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_MEMBER_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_MEMBER_ERROR.getMessage());
        }
        return resultVO;
    }

    /**
     * 커뮤니티 정보 수정 — 표시 이름·설명·공개 범위·로고·배너. 운영진만. slug(commNm)는 주소라 바꾸지 않는다.
     * 멀티파트: commSn, commDsplNm, commDesc, prvcyStng, logoImage?, bannerImage?, removeLogo?, removeBanner?
     */
    @Transactional
    public ResultVO updateCommunity(MultipartHttpServletRequest request, Long me) {
        ResultVO resultVO = new ResultVO();
        try {
            long commSn = Long.parseLong(request.getParameter("commSn"));
            if (!isManager(roleOf(commSn, me))) throw new IllegalArgumentException("운영진만 고칠 수 있습니다.");
            TblComm comm = tblCommRepository.findByCommSn(commSn);
            if (comm == null) throw new IllegalArgumentException("존재하지 않는 커뮤니티입니다.");

            List<String> changed = new java.util.ArrayList<>();   // 운영 기록용 — 무엇이 바뀌었나
            String dspl = request.getParameter("commDsplNm");
            if (dspl != null && dspl.trim().length() >= 2 && !dspl.trim().equals(comm.getCommDsplNm())) {
                changed.add("이름 「" + comm.getCommDsplNm() + "」→「" + dspl.trim() + "」");
                comm.setCommDsplNm(dspl.trim());
            }
            String desc = request.getParameter("commDesc");
            if (desc != null && !desc.trim().equals(comm.getCommDesc() == null ? "" : comm.getCommDesc())) {
                changed.add("소개");
                comm.setCommDesc(desc.trim());
            }
            String prv = request.getParameter("prvcyStng");
            if (prv != null && java.util.Set.of("public", "restricted", "anonymous", "private").contains(prv) && !prv.equals(comm.getPrvcyStng())) {
                // 공개 범위는 OWNER 만 — 비공개를 공개로 돌리면 지난 글 전부가 피드·검색에 열린다. 운영진(ADMIN)이 혼자 바꿀 일이 아니다.
                if (!"OWNER".equals(roleOf(commSn, me))) throw new IllegalArgumentException("공개 범위는 커뮤니티 주인만 바꿀 수 있어요.");
                Map<String, String> label = Map.of("public", "공개", "restricted", "제한적", "anonymous", "익명", "private", "비공개");
                changed.add("공개 범위 " + label.getOrDefault(String.valueOf(comm.getPrvcyStng()), "?") + "→" + label.get(prv));
                comm.setPrvcyStng(prv);
            }

            // 주 언어 — 빈 값이면 「여러 언어」
            if (request.getParameter("commLang") != null) {
                String lang = com.knowra.cmm.i18n.LangDetect.commLang(request.getParameter("commLang"));
                if (!java.util.Objects.equals(lang, comm.getLang())) { changed.add("언어"); comm.setLang(lang); }
            }

            String psnTblSn = "community_" + commSn;
            if (request.getFile("logoImage") != null) {
                TblComFile f = fileUtil.storeImage(request.getFile("logoImage"), me, FileUtil.CAT_COMMUNITY_LOGO, psnTblSn);
                f.setCreatrSn(me);
                comm.setLogoFileSn(tblComFileRepository.save(f).getAtchFileSn());
                changed.add("로고");
            } else if ("true".equals(request.getParameter("removeLogo")) && comm.getLogoFileSn() != null) {
                comm.setLogoFileSn(null);
                changed.add("로고 삭제");
            }
            if (request.getFile("bannerImage") != null) {
                TblComFile f = fileUtil.storeImage(request.getFile("bannerImage"), me, FileUtil.CAT_COMMUNITY_BANNER, psnTblSn);
                f.setCreatrSn(me);
                comm.setBnrFileSn(tblComFileRepository.save(f).getAtchFileSn());
                changed.add("배너");
            } else if ("true".equals(request.getParameter("removeBanner")) && comm.getBnrFileSn() != null) {
                comm.setBnrFileSn(null);
                changed.add("배너 삭제");
            }
            comm.setMdfrSn(me);
            tblCommRepository.save(comm);
            if (!changed.isEmpty()) modLog.record(commSn, me, com.knowra.community.modlog.ModLogService.SETTINGS, null, null, String.join(", ", changed));
            resultVO.setResultCode(ResponseCode.COMMUNITY_UPDATE_SUCCESS.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_UPDATE_SUCCESS.getMessage());
        } catch (IllegalArgumentException e) {
            resultVO.setResultCode(ResponseCode.COMMUNITY_UPDATE_ERROR.getCode());
            resultVO.setResultMessage(e.getMessage());
        } catch (Exception e) {
            ErrorLog.report(e);
            resultVO.setResultCode(ResponseCode.COMMUNITY_UPDATE_ERROR.getCode());
            resultVO.setResultMessage(ResponseCode.COMMUNITY_UPDATE_ERROR.getMessage());
        }
        return resultVO;
    }
}
