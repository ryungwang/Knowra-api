package com.knowra.community.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.community.entity.TblComm;
import com.knowra.community.service.CommunityExploreService;
import com.knowra.community.service.CommunityPostService;
import com.knowra.community.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Map;

@RestController
@CrossOrigin("*")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final CommunityExploreService communityExploreService;

    @GetMapping("/api/community/getCommunityList")
    public ResultVO getCommunityList(Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityService.getCommunityList(params, token);
    }

    @PostMapping("/api/community/setCommunity")
    public ResultVO setCommunity(
            @ModelAttribute TblComm tblComm,
            MultipartHttpServletRequest request,
            HttpServletRequest httpServletRequest) {
        String token = httpServletRequest.getHeader("Authorization");
        return communityService.setCommunity(tblComm, request, token);
    }

    @GetMapping("/api/community/getCommunity")
    public ResultVO getCommunity(@RequestParam("commNm") String commNm, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityService.getCommunity(commNm, token);
    }

    @PostMapping("/api/community/getMyCommunityList")
    public ResultVO getMyCommunityList(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityService.getMyCommunityList(token);
    }

    @PostMapping("/api/community/setMember")
    public ResultVO setMember(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityService.setMember(params, token);
    }

    @GetMapping("/api/community/explore")
    public ResultVO getExploreCommunities(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityExploreService.getExploreCommunities(token);
    }

    @GetMapping("/api/community/explore/filter")
    public ResultVO getExploreFilterCommunities(@RequestParam Map<String, Object> params, HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return communityExploreService.getExploreFilterCommunities(params, token);
    }
}
