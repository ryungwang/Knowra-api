package com.knowra.cmm.service;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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

    private RedisTemplate<String, Object> getRedisTemplate(int templateIndex) {
        String beanName = "redisTemplate" + templateIndex;
        return (RedisTemplate<String, Object>) context.getBean(beanName);
    }
}