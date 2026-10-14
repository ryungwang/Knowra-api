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
