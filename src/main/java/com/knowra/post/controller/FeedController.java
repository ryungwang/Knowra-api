package com.knowra.post.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.post.service.FeedService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin("*")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /** 홈 피드 — 전체 개인화 (토큰 선택) */
    @GetMapping("/api/feed/personalized")
    public ResultVO getPersonalizedFeed(@RequestParam Map<String, Object> params,
                                         HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return feedService.getPersonalizedFeed(params, token);
    }

    /** 팔로잉 피드 — 팔로우한 유저 + 가입 커뮤니티 (토큰 선택) */
    @GetMapping("/api/feed/following")
    public ResultVO getFollowingFeed(@RequestParam Map<String, Object> params,
                                      HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return feedService.getFollowingFeed(params, token);
    }

    /** 인기 피드 — 품질점수 강화 (토큰 선택) */
    @GetMapping("/api/feed/popular")
    public ResultVO getPopularFeed(@RequestParam Map<String, Object> params,
                                    HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return feedService.getPopularFeed(params, token);
    }

    /** 최신 피드 — 신선도점수 강화 (토큰 선택) */
    @GetMapping("/api/feed/latest")
    public ResultVO getLatestFeed(@RequestParam Map<String, Object> params,
                                   HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return feedService.getLatestFeed(params, token);
    }
}
