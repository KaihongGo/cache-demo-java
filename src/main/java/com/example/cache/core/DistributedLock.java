package com.example.cache.core;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class DistributedLock {
    private final StringRedisTemplate redis;

    public DistributedLock(StringRedisTemplate redis) { 
        this.redis = redis; 
    }

    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent("lock:" + key, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    public void unlock(String key, String token) {
        String lockKey = "lock:" + key;
        String current = redis.opsForValue().get(lockKey);
        if (token != null && token.equals(current)) {
            redis.delete(lockKey);
        }
    }
}