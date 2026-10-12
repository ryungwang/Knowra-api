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
 *
 * <p><b>ZSET 의 score 는 오직 {@code FeedService.computeScore} 의 결과다.</b> 다른 어떤
 * 값도 넣지 않는다. 예전에는 세 곳이 서로 다른 의미로 같은 키에 썼다 — 글 생성은 0,
 * 반응은 좋아요 2.0/댓글 4.0 누적, 캐시 미스 재구축은 computeScore 결과. 랭킹 점수가
 * 정규화되어 [0,1] 이 된 뒤로는 반응 한 건(+2.0)이 최상위 글(0.338)의 6배가 된다.
 *
 * <p>증분 갱신은 되돌릴 수 없다. 품질 축이 {@code saturate(반응량 / (경과시간 + 4))} 라
 * 반응 한 건이 총점에 주는 영향이 글의 나이와 현재 점수에 따라 달라진다. 상수 델타로
 * 표현할 수 있는 값이 아니다. 그래서 갱신은 전부 <b>무효화</b> 로 처리하고, 다음 조회가
 * 전체를 다시 계산한다.
 */
@Service
public class FeedCacheService {

    private static final int    DB       = 14;
    private static final int    MAX_SIZE = 500;
    private static final Duration PERSONALIZED_TTL = Duration.ofMinutes(30);

    /**
     * 전역 피드(인기·최신)의 TTL.
     * 반응은 캐시를 무효화하지 않으므로(빈도가 높아 매번 재구축하면 비싸다) 이 시간만큼
     * 순서가 늦게 반영된다. 새 글은 {@code FeedEventListener} 가 즉시 무효화하므로
     * TTL 을 기다리지 않는다.
     */
    private static final Duration GLOBAL_TTL = Duration.ofMinutes(5);

    private static final String FOLLOWING_PREFIX    = "feed:following:";
    private static final String PERSONALIZED_PREFIX = "feed:personalized:";
    private static final String POPULAR_KEY         = "feed:popular";
    private static final String EXPLORE_SUFFIX      = ":explore";
    private static final String LATEST_KEY          = "feed:latest";
    private static final String TRENDING_KEY        = "feed:trending";

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

    /**
     * 탐험 쿼터로 끌어올린 글의 목록(Set).
     * 어떤 글이 쿼터로 올라왔는지는 근거 계산에 필요한데, 정렬 결과(ZSET)만으로는 알 수
     * 없다. 캐시 히트 경로에서도 같은 근거가 나오려면 따로 남겨야 한다.
     */
    public String exploreSlotsKey(long userSn)  { return personalizedKey(userSn) + EXPLORE_SUFFIX; }
    public String latestKey()                  { return LATEST_KEY; }
    public String trendingKey()                { return TRENDING_KEY; }

    // ── 벌크 ZADD (캐시 미스 시 전체 populate) ──────────────────────────────
    // 유일한 쓰기 경로다. 단건 add 와 incrementScore 는 랭킹 점수와 다른 척도를 넣던
    // 통로라 제거했다 — 자세한 이유는 클래스 주석.

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

    // ── 페이지 읽기 (score DESC) ─────────────────────────────────────────────
    public List<String> getPage(String key, int pageIndex, int pageSize) {
        long from = (long) pageIndex * pageSize;
        long to   = from + pageSize - 1;
        Set<Object> result = redis().opsForZSet().reverseRange(key, from, to);
        if (result == null) return List.of();
        return result.stream().map(Object::toString).toList();
    }

    // ── 탐험 슬롯 목록 ──────────────────────────────────────────────────────
    public void putExploreSlots(String key, Set<String> members) {
        RedisTemplate<String, Object> r = redis();
        r.delete(key);
        if (members.isEmpty()) return;
        r.opsForSet().add(key, members.toArray());
        r.expire(key, PERSONALIZED_TTL);
    }

    public Set<String> getExploreSlots(String key) {
        Set<Object> members = redis().opsForSet().members(key);
        if (members == null || members.isEmpty()) return Set.of();
        return members.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
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

    // ── TTL 설정 ────────────────────────────────────────────────────────────
    /** PERSONALIZED: 30분 안전망. 관심사·팔로우 변화가 이벤트로 안 잡히는 경우 대비. */
    public void expirePersonalized(String key) {
        redis().expire(key, PERSONALIZED_TTL);
    }

    /** 전역 피드(인기·최신): 반응을 반영하는 유일한 수단. */
    public void expireGlobal(String key) {
        redis().expire(key, GLOBAL_TTL);
    }

    /** 전역 피드 전체 무효화 — 새 글이 올라왔을 때. */
    public void invalidateGlobalFeeds() {
        invalidate(popularKey());
        invalidate(latestKey());
        invalidate(trendingKey());
    }

    /**
     * PERSONALIZED 무효화 — 탐험 슬롯 목록도 함께 버린다.
     * 순서가 다시 계산되면 어떤 글이 쿼터로 올라올지도 달라지므로 둘은 같이 움직여야 한다.
     */
    public void invalidatePersonalized(long userSn) {
        invalidate(personalizedKey(userSn));
        invalidate(exploreSlotsKey(userSn));
    }

    // ── 유저의 모든 피드 캐시 무효화 ────────────────────────────────────────
    public void invalidateUserFeeds(long userSn) {
        invalidate(followingKey(userSn));
        invalidatePersonalized(userSn);
    }
}
