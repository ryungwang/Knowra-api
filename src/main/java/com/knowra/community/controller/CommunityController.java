package com.knowra.community.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.community.entity.TblCommunities;
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

    @PostMapping("/api/community/setCommunity")
    public ResultVO setCommunity(
            @ModelAttribute TblCommunities tblCommunities,
            MultipartHttpServletRequest request,
            HttpServletRequest httpServletRequest) {
        String token = httpServletRequest.getHeader("Authorization");
        return communityService.setCommunity(tblCommunities, request, token);
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
}
