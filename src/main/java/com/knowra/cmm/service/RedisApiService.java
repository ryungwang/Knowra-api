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
    public void incrementViewCount(int templateIndex, long postSn) {
        RedisTemplate<String, Object> redisTemplate = getRedisTemplate(templateIndex);
        redisTemplate.opsForValue().increment("post:viewcnt:" + postSn);
    }

    // 동기화 대상 키 전체 조회
    public Set<String> getViewCountKeys(int templateIndex) {
        RedisTemplate<String, Object> redisTemplate = getRedisTemplate(templateIndex);
        return redisTemplate.keys("post:viewcnt:*");
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
