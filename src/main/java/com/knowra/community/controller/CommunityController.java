package com.knowra.community.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.community.entity.TblComm;
import com.knowra.community.service.CommunityExploreService;
import com.knowra.community.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final CommunityExploreService communityExploreService;
    private final com.knowra.community.modlog.ModLogService modLogService;

    private final com.knowra.community.stats.CommunityStatsService statsService;

    /** 커뮤니티 통계(최근 30일) — 운영진만 */
    @GetMapping("/api/community/stats")
    public ResultVO stats(@RequestParam long commSn, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        try {
            if (me == null) throw new IllegalArgumentException("로그인이 필요해요.");
            statsService.stats(commSn, me).forEach(r::putResult);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SELECT_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        } catch (Exception e) {
            com.knowra.cmm.errlog.ErrorLog.report(e);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SELECT_ERROR.getCode());
            r.setResultMessage(com.knowra.cmm.model.ResponseCode.SELECT_ERROR.getMessage());
        }
        return r;
    }

    private final com.knowra.community.report.CommunityReportService reportService;

    private final com.knowra.community.flair.FlairService flairService;

    /** 말머리 목록 — 누구나 */
    @GetMapping("/api/community/flairs")
    public ResultVO flairs(@RequestParam long commSn) {
        ResultVO r = new ResultVO();
        r.putResult("list", flairService.list(commSn));
        r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** 말머리 통째로 바꾸기 — body: commSn, items[{flairSn?, name, color}] */
    @PostMapping("/api/community/flairs/save")
    public ResultVO saveFlairs(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        try {
            r.putResult("list", flairService.save(Long.parseLong(String.valueOf(b.get("commSn"))), (java.util.List<?>) b.get("items"), me));
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(com.knowra.cmm.model.ResponseCode.UPDATE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        } catch (Exception e) {
            com.knowra.cmm.errlog.ErrorLog.report(e);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.UPDATE_ERROR.getCode());
            r.setResultMessage(com.knowra.cmm.model.ResponseCode.UPDATE_ERROR.getMessage());
        }
        return r;
    }

    private final com.knowra.community.joinq.JoinQuestionService joinQuestions;

    private final com.knowra.community.hold.HoldService holdService;
    private final com.knowra.post.service.PostService postService;

    /** 새 멤버 글 검토 — 검토할 글 목록(운영진). body: commSn */
    @PostMapping("/api/community/hold/list")
    public ResultVO holdList(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return holdRun(r -> r.putResult("list", holdService.list(Long.parseLong(String.valueOf(b.get("commSn"))), me)));
    }

    /** 승인·거절. body: postSn, approve(true|false) */
    @PostMapping("/api/community/hold/decide")
    public ResultVO holdDecide(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return holdRun(r -> {
            Long released = holdService.decide(Long.parseLong(String.valueOf(b.get("postSn"))), me, Boolean.parseBoolean(String.valueOf(b.get("approve"))));
            if (released != null) postService.releaseHeld(released);
        });
    }

    /** 검토 기간(날수, 0=끔). body: commSn, days */
    @PostMapping("/api/community/hold/days")
    public ResultVO holdDays(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        return holdRun(r -> holdService.setDays(Long.parseLong(String.valueOf(b.get("commSn"))), me, Integer.parseInt(String.valueOf(b.get("days")))));
    }

    private static ResultVO holdRun(java.util.function.Consumer<ResultVO> body) {
        ResultVO r = new ResultVO();
        try {
            body.accept(r);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(com.knowra.cmm.model.ResponseCode.UPDATE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        }
        return r;
    }

    /** 가입 질문 — 누구나(신청하기 전에 읽는다) */
    @GetMapping("/api/community/joinQuestions")
    public ResultVO joinQuestions(@RequestParam long commSn) {
        ResultVO r = new ResultVO();
        r.putResult("list", joinQuestions.list(commSn));
        r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** 가입 질문 통째로 바꾸기(운영진) — body: commSn, questions[최대 3] */
    @PostMapping("/api/community/joinQuestions/save")
    public ResultVO saveJoinQuestions(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        try {
            r.putResult("list", joinQuestions.save(Long.parseLong(String.valueOf(b.get("commSn"))), me, (java.util.List<?>) b.get("questions")));
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(com.knowra.cmm.model.ResponseCode.UPDATE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        }
        return r;
    }

    /** 운영진 신고함 — stat: OPEN | DONE */
    @GetMapping("/api/community/reports")
    public ResultVO reports(@RequestParam long commSn, @RequestParam(defaultValue = "OPEN") String stat, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        try {
            if (me == null) throw new IllegalArgumentException("로그인이 필요해요.");
            r.putResult("list", reportService.list(commSn, stat, me));
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SELECT_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        } catch (Exception e) {
            com.knowra.cmm.errlog.ErrorLog.report(e);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SELECT_ERROR.getCode());
            r.setResultMessage(com.knowra.cmm.model.ResponseCode.SELECT_ERROR.getMessage());
        }
        return r;
    }

    /** body: commSn, targetKind(POST|CMT), targetSn, action(RESOLVE|DISMISS) */
    @PostMapping("/api/community/reports/handle")
    public ResultVO handleReport(@RequestBody Map<String, Object> b, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        try {
            int n = reportService.handle(Long.parseLong(String.valueOf(b.get("commSn"))), String.valueOf(b.get("targetKind")),
                    Long.parseLong(String.valueOf(b.get("targetSn"))), String.valueOf(b.get("action")), me);
            r.putResult("handled", n);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(com.knowra.cmm.model.ResponseCode.UPDATE_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        } catch (Exception e) {
            com.knowra.cmm.errlog.ErrorLog.report(e);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.UPDATE_ERROR.getCode());
            r.setResultMessage(com.knowra.cmm.model.ResponseCode.UPDATE_ERROR.getMessage());
        }
        return r;
    }

    /** 운영 기록 — 운영진만. cursor: 이보다 오래된 logSn */
    @GetMapping("/api/community/modLog")
    public ResultVO modLog(@RequestParam long commSn, @RequestParam(required = false) Long cursor, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        try {
            if (me == null) throw new IllegalArgumentException("로그인이 필요해요.");
            modLogService.list(commSn, cursor, me).forEach(r::putResult);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        } catch (IllegalArgumentException e) {
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SELECT_ERROR.getCode());
            r.setResultMessage(e.getMessage());
        } catch (Exception e) {
            com.knowra.cmm.errlog.ErrorLog.report(e);
            r.setResultCode(com.knowra.cmm.model.ResponseCode.SELECT_ERROR.getCode());
            r.setResultMessage(com.knowra.cmm.model.ResponseCode.SELECT_ERROR.getMessage());
        }
        return r;
    }

    @GetMapping("/api/community/getCommunityList")
    public ResultVO getCommunityList(Map<String, Object> params, @CurrentUser Long me) {
        return communityService.getCommunityList(params, me);
    }

    @PostMapping("/api/community/setCommunity")
    public ResultVO setCommunity(
            @ModelAttribute TblComm tblComm,
            MultipartHttpServletRequest request,
            @CurrentUser Long me) {
        return communityService.setCommunity(tblComm, request, me);
    }

    @GetMapping("/api/community/getCommunity")
    public ResultVO getCommunity(@RequestParam("commNm") String commNm, @CurrentUser Long me) {
        return communityService.getCommunity(commNm, me);
    }

    @PostMapping("/api/community/getMyCommunityList")
    public ResultVO getMyCommunityList(@CurrentUser Long me) {
        return communityService.getMyCommunityList(me);
    }

    /** 멤버 목록. params: commSn, stat(ACTIVE|PENDING|BANNED) */
    @PostMapping("/api/community/getMemberList")
    public ResultVO getMemberList(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityService.getMemberList(params, me);
    }

    /** 가입 승인·거절·강퇴. params: commSn, userSn, stat */
    @PostMapping("/api/community/setMemberStat")
    public ResultVO setMemberStat(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityService.setMemberStat(params, me);
    }

    /** 운영진 지정·해제. params: commSn, userSn, role */
    @PostMapping("/api/community/setMemberRole")
    public ResultVO setMemberRole(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityService.setMemberRole(params, me);
    }

    /** 주인 넘기기. params: commSn, userSn, confirm(커뮤니티 이름) */
    @PostMapping("/api/community/transferOwner")
    public ResultVO transferOwner(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityService.transferOwner(params, me);
    }

    /** 커뮤니티 정보 수정(멀티파트). */
    @PostMapping("/api/community/updateCommunity")
    public ResultVO updateCommunity(MultipartHttpServletRequest request, @CurrentUser Long me) {
        return communityService.updateCommunity(request, me);
    }

    /** 새 글 알림 켜기/끄기 — 활성 멤버만. params: commSn, notifOn */
    @PostMapping("/api/community/setNotif")
    public ResultVO setNotif(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityService.setNotif(params, me);
    }

    @PostMapping("/api/community/setMember")
    public ResultVO setMember(@RequestBody Map<String, Object> params, @CurrentUser Long me) {
        return communityService.setMember(params, me);
    }

    @GetMapping("/api/community/explore")
    public ResultVO getExploreCommunities(@CurrentUser Long me) {
        return communityExploreService.getExploreCommunities(me);
    }

    @GetMapping("/api/community/explore/filter")
    public ResultVO getExploreFilterCommunities(@RequestParam Map<String, Object> params, @CurrentUser Long me) {
        return communityExploreService.getExploreFilterCommunities(params, me);
    }
}
