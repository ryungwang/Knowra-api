package com.knowra.community.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.community.entity.TblCommunities;
import com.knowra.community.service.CommunityService;
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
            MultipartHttpServletRequest request) {
        return communityService.setCommunity(tblCommunities, request);
    }

    @GetMapping("/api/community/getCommunity")
    public ResultVO setWalletAddress(@RequestParam("communityNm") String communityNm) {
        return communityService.getCommunity(communityNm);
    }
}
