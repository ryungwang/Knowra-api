package com.knowra.cmm.ratelimit;

import com.knowra.cmm.service.RedisApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Redis 고정 창 카운터. 서버가 여러 대여도 같은 Redis 를 보므로 한도가 합쳐서 걸린다.
 *
 * <p>⚠️ Redis 가 죽으면 <b>막지 않고 통과</b>시킨다 — 제한기 장애로 로그인·글쓰기가 전부
 * 멈추는 게 도배보다 훨씬 나쁘다.
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    /** 다른 용도(블록리스트·인증 코드 등)와 섞이지 않게 키 접두사로 구분한다. */
    private static final int REDIS_DB = 15;
    private static final String PREFIX = "rl:";

    private final RedisApiService redis;

    /** 한 번 세고, 이번 것까지 한도 안이면 true. */
    public boolean tryHit(String key, int limit, long windowSec) {
        try {
            return redis.incr(REDIS_DB, PREFIX + key, windowSec) <= limit;
        } catch (Exception e) {
            return true;
        }
    }

    /** 세지 않고 이미 한도를 넘었는지만 본다. */
    public boolean isOver(String key, int limit) {
        try {
            Object v = redis.getRedis(REDIS_DB, PREFIX + key);
            return v != null && Long.parseLong(v.toString()) >= limit;
        } catch (Exception e) {
            return false;
        }
    }

    /** 세기만 한다(실패 횟수처럼 "일이 일어난 뒤" 세는 자리). */
    public void count(String key, long windowSec) {
        try { redis.incr(REDIS_DB, PREFIX + key, windowSec); } catch (Exception ignored) { }
    }

    public void reset(String key) {
        try { redis.delRedis(REDIS_DB, PREFIX + key); } catch (Exception ignored) { }
    }

    /** 다시 시도할 수 있을 때까지 남은 분(올림, 최소 1). */
    public long minutesLeft(String key) {
        try { return Math.max(1, (redis.ttlSeconds(REDIS_DB, PREFIX + key) + 59) / 60); } catch (Exception e) { return 1; }
    }
}
