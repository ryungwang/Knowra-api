package com.knowra.community.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.community.entity.TblCommunity;
import com.knowra.community.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@CrossOrigin("*")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @PostMapping("/api/community/setCommunity")
    public ResultVO setCommunity(
            @ModelAttribute TblCommunity tblCommunity,
            MultipartHttpServletRequest request) {
        return communityService.setCommunity(tblCommunity, request);
    }

//    @PostMapping("/api/user/setWalletAddress")
//    public ResultVO setWalletAddress(@RequestBody Map<String, Object> params) {
//        return userService.setWalletAddress(params);
//    }
}
