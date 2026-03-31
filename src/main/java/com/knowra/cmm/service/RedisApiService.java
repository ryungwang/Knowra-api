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
