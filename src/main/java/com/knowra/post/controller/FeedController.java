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
        return stamp(feedService.getPersonalizedFeed(params, me));
    }

    /** 팔로잉 피드 — 팔로우한 유저 + 가입 커뮤니티 (토큰 선택) */
    @GetMapping("/api/feed/following")
    public ResultVO getFollowingFeed(@RequestParam Map<String, Object> params,
                                      @CurrentUser Long me) {
        return stamp(feedService.getFollowingFeed(params, me));
    }

    /** 인기 피드 — 품질점수 강화 (토큰 선택) */
    @GetMapping("/api/feed/popular")
    public ResultVO getPopularFeed(@RequestParam Map<String, Object> params,
                                    @CurrentUser Long me) {
        return stamp(feedService.getPopularFeed(params, me));
    }

    /** 최신 피드 — 신선도점수 강화 (토큰 선택) */
    @GetMapping("/api/feed/latest")
    public ResultVO getLatestFeed(@RequestParam Map<String, Object> params,
                                   @CurrentUser Long me) {
        return stamp(feedService.getLatestFeed(params, me));
    }

    @GetMapping("/api/feed/trending")
    public ResultVO getTrendingFeed(@RequestParam Map<String, Object> params,
                                   @CurrentUser Long me) {
        return stamp(feedService.getTrendingFeed(params, me));
    }

    /** 새 글 수 — 피드를 받은 뒤(since = 그 응답의 asOf) 올라온 글. 「새 글 N개」 띠용. type: personalized·following·… */
    @GetMapping("/api/feed/newCount")
    public ResultVO newCount(@RequestParam String since, @RequestParam(defaultValue = "personalized") String type, @CurrentUser Long me) {
        ResultVO r = new ResultVO();
        r.putResult("count", feedService.newCount(java.time.LocalDateTime.parse(since), type, me));
        r.putResult("asOf", java.time.LocalDateTime.now());
        r.setResultCode(com.knowra.cmm.model.ResponseCode.SUCCESS.getCode());
        return r;
    }

    /** 응답에 서버 시각을 붙인다 — 「새 글」 기준을 화면 시계가 아니라 서버 시계로 잡기 위해 */
    private static ResultVO stamp(ResultVO r) {
        r.putResult("asOf", java.time.LocalDateTime.now());
        return r;
    }
}
