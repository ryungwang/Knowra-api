package com.knowra.post.service;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 피드 캐시 전담 서비스 — Redis DB 14 사용.
 * 모든 피드는 Sorted Set(score DESC = 최우선)으로 저장.
 */
@Service
public class FeedCacheService {

    private static final int    DB       = 14;
    private static final int    MAX_SIZE = 500;
    private static final Duration PERSONALIZED_TTL = Duration.ofMinutes(30);

    private static final String FOLLOWING_PREFIX    = "feed:following:";
    private static final String PERSONALIZED_PREFIX = "feed:personalized:";
    private static final String POPULAR_KEY         = "feed:popular";
    private static final String LATEST_KEY          = "feed:latest";

    private final GenericApplicationContext context;

    public FeedCacheService(GenericApplicationContext context) {
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> redis() {
        return (RedisTemplate<String, Object>) context.getBean("redisTemplate" + DB);
    }

    // ── 키 생성 ─────────────────────────────────────────────────────────────
    public String followingKey(long userSn)    { return FOLLOWING_PREFIX    + userSn; }
    public String personalizedKey(long userSn) { return PERSONALIZED_PREFIX + userSn; }
    public String popularKey()                 { return POPULAR_KEY; }
    public String latestKey()                  { return LATEST_KEY; }

    // ── 단건 추가 + 크기 제한 ────────────────────────────────────────────────
    public void add(String key, String member, double score) {
        RedisTemplate<String, Object> r = redis();
        r.opsForZSet().add(key, member, score);
        // score 낮은 순(오래된/비인기) 초과분 제거
        r.opsForZSet().removeRange(key, 0, -(MAX_SIZE + 2));
    }

    // ── 벌크 ZADD (캐시 미스 시 전체 populate) ──────────────────────────────
    public void populate(String key, Map<String, Double> memberScores) {
        if (memberScores.isEmpty()) return;
        RedisTemplate<String, Object> r = redis();
        Set<ZSetOperations.TypedTuple<Object>> tuples = new LinkedHashSet<>();
        for (Map.Entry<String, Double> e : memberScores.entrySet()) {
            tuples.add(ZSetOperations.TypedTuple.of(e.getKey(), e.getValue()));
        }
        r.opsForZSet().add(key, tuples);
        r.opsForZSet().removeRange(key, 0, -(MAX_SIZE + 2));
    }

    // ── POPULAR 점수 증분 ────────────────────────────────────────────────────
    public void incrementScore(String key, String member, double delta) {
        redis().opsForZSet().incrementScore(key, member, delta);
    }

    // ── 페이지 읽기 (score DESC) ─────────────────────────────────────────────
    public List<String> getPage(String key, int pageIndex, int pageSize) {
        long from = (long) pageIndex * pageSize;
        long to   = from + pageSize - 1;
        Set<Object> result = redis().opsForZSet().reverseRange(key, from, to);
        if (result == null) return List.of();
        return result.stream().map(Object::toString).toList();
    }

    // ── 개별 항목 제거 (게시글 삭제 시) ─────────────────────────────────────
    public void remove(String key, String member) {
        redis().opsForZSet().remove(key, member);
    }

    // ── 크기 조회 ────────────────────────────────────────────────────────────
    public long size(String key) {
        Long sz = redis().opsForZSet().size(key);
        return sz == null ? 0 : sz;
    }

    // ── 캐시 존재 여부 ───────────────────────────────────────────────────────
    public boolean exists(String key) {
        return size(key) > 0;
    }

    // ── 캐시 무효화 ──────────────────────────────────────────────────────────
    public void invalidate(String key) {
        redis().delete(key);
    }

    // ── TTL 설정 (PERSONALIZED: 30분 안전망) ────────────────────────────────
    public void expirePersonalized(String key) {
        redis().expire(key, PERSONALIZED_TTL);
    }

    // ── 유저의 모든 피드 캐시 무효화 ────────────────────────────────────────
    public void invalidateUserFeeds(long userSn) {
        invalidate(followingKey(userSn));
        invalidate(personalizedKey(userSn));
    }
}
