package com.example.campaign.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
        log.debug("Redis SET key={} ttl={}", key, ttl);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        Boolean result = redisTemplate.delete(key);
        log.debug("Redis DELETE key={} result={}", key, result);
        return result;
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    // ─── LIST operations (campaign contact queue) ───────────

    /** Push to right (enqueue) */
    public void pushToQueue(String key, Object value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    /** Pop from left (dequeue) — LPOP */
    public Object popFromQueue(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    public Long getQueueSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    // ─── SET operations (deduplication) ─────────────────────

    public void addToSet(String key, Object value) {
        redisTemplate.opsForSet().add(key, value);
    }

    public Boolean isInSet(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    // ─── HASH operations (campaign metadata) ────────────────

    public void setHash(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public Object getHash(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    public Map<Object, Object> getAllHash(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    // ─── Atomic increment (rate limiting / semaphore) ────────

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    public Long decrement(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }

    public void setExpiry(String key, Duration ttl) {
        redisTemplate.expire(key, ttl);
        log.debug("Redis EXPIRE key={} ttl={}", key, ttl);
    }
}
