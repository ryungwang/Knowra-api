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
