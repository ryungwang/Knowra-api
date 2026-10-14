package com.knowra.cmm.service;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisApiService {

    private final GenericApplicationContext context;

    public RedisApiService(GenericApplicationContext context) {
        this.context = context;
    }

    public void setRedis(int templateIndex, String key, Object value, Integer expireTime) {
        RedisTemplate<String, Object> redisTemplate = getRedisTemplate(templateIndex);
        if(expireTime == null){
            redisTemplate.opsForValue().set(key, value);
        }else{
            redisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.SECONDS);
        }
    }

    /** 없을 때만 넣는다(원자적). 넣었으면 true — 같은 일을 두 번 하지 않으려는 자리에 쓴다. */
    public boolean setIfAbsent(int templateIndex, String key, Object value, long ttlSeconds) {
        return Boolean.TRUE.equals(getRedisTemplate(templateIndex).opsForValue().setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS));
    }

    /**
     * +1 하고 새 값을 돌려준다. 키가 이번에 생겼거나 만료가 빠져 있으면 {@code ttlSeconds} 를 건다
     * — 고정 창(window) 카운터. 만료 설정이 따로 실패해도 다음 호출이 다시 건다.
     */
    public long incr(int templateIndex, String key, long ttlSeconds) {
        RedisTemplate<String, Object> t = getRedisTemplate(templateIndex);
        Long n = t.opsForValue().increment(key);
        Long ttl = t.getExpire(key, TimeUnit.SECONDS);
        if (n != null && (n == 1 || ttl == null || ttl < 0)) t.expire(key, ttlSeconds, TimeUnit.SECONDS);
        return n == null ? 0 : n;
    }

    /** 남은 만료 시간(초). 키가 없으면 0. */
    public long ttlSeconds(int templateIndex, String key) {
        Long ttl = getRedisTemplate(templateIndex).getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    public Object getRedis(int templateIndex, String key) {
        RedisTemplate<String, Object> redisTemplate = getRedisTemplate(templateIndex);
        return redisTemplate.opsForValue().get(key);
    }

    public void delRedis(int templateIndex, String key) {
        RedisTemplate<String, Object> redisTemplate = getRedisTemplate(templateIndex);
        redisTemplate.delete(key);
    }

    // 게시글 조회수 +1 (delta 누적)
    public void incrementViewCount(int templateIndex, String type, long postSn) {
        RedisTemplate<String, Object> redisTemplate = getRedisTemplate(templateIndex);
        redisTemplate.opsForValue().increment(type + ":viewcnt:" + postSn);
    }

    // 동기화 대상 키 전체 조회
    public Set<String> getViewCountKeys(int templateIndex) {
        RedisTemplate<String, Object> redisTemplate = getRedisTemplate(templateIndex);
        return redisTemplate.keys("*:viewcnt:*");
    }

    // 로그아웃 토큰 블랙리스트 등록 (TTL = 토큰 남은 유효시간)
    public void addToBlocklist(int templateIndex, String token, long ttlSeconds) {
        RedisTemplate<String, Object> redisTemplate = getRedisTemplate(templateIndex);
        redisTemplate.opsForValue().set("auth:blocklist:" + token, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    // 블랙리스트 여부 확인
    public boolean isBlocklisted(int templateIndex, String token) {
        RedisTemplate<String, Object> redisTemplate = getRedisTemplate(templateIndex);
        return Boolean.TRUE.equals(redisTemplate.hasKey("auth:blocklist:" + token));
    }

    // 값 가져오고 삭제 (동기화 후 리셋)
    public long getAndDeleteViewCount(int templateIndex, String key) {
        RedisTemplate<String, Object> redisTemplate = getRedisTemplate(templateIndex);
        Object value = redisTemplate.opsForValue().getAndDelete(key);
        if (value == null) return 0L;
        return Long.parseLong(value.toString());
    }

    private RedisTemplate<String, Object> getRedisTemplate(int templateIndex) {
        String beanName = "redisTemplate" + templateIndex;
        return (RedisTemplate<String, Object>) context.getBean(beanName);
    }
}
