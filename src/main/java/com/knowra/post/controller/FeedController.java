package com.knowra.post.controller;

import com.knowra.cmm.model.ResultVO;
import com.knowra.cmm.security.CurrentUser;
import com.knowra.post.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /** 홈 피드 — 전체 개인화 (토큰 선택) */
    @GetMapping("/api/feed/personalized")
    public ResultVO getPersonalizedFeed(@RequestParam Map<String, Object> params,
                                         @CurrentUser Long me) {
        return feedService.getPersonalizedFeed(params, me);
    }

    /** 팔로잉 피드 — 팔로우한 유저 + 가입 커뮤니티 (토큰 선택) */
    @GetMapping("/api/feed/following")
    public ResultVO getFollowingFeed(@RequestParam Map<String, Object> params,
                                      @CurrentUser Long me) {
        return feedService.getFollowingFeed(params, me);
    }

    /** 인기 피드 — 품질점수 강화 (토큰 선택) */
    @GetMapping("/api/feed/popular")
    public ResultVO getPopularFeed(@RequestParam Map<String, Object> params,
                                    @CurrentUser Long me) {
        return feedService.getPopularFeed(params, me);
    }

    /** 최신 피드 — 신선도점수 강화 (토큰 선택) */
    @GetMapping("/api/feed/latest")
    public ResultVO getLatestFeed(@RequestParam Map<String, Object> params,
                                   @CurrentUser Long me) {
        return feedService.getLatestFeed(params, me);
    }

    @GetMapping("/api/feed/trending")
    public ResultVO getTrendingFeed(@RequestParam Map<String, Object> params,
                                   @CurrentUser Long me) {
        return feedService.getTrendingFeed(params, me);
    }
}
