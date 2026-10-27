package com.novabank.txn.service;

import com.novabank.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RedisAssistantRateLimiter implements AssistantRateLimiter {
    private static final long MAX_REQUESTS_PER_MINUTE = 20;
    private final StringRedisTemplate redis;

    public RedisAssistantRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void check(Long userId) {
        String key = "assistant:rate:" + userId + ":" + (Instant.now().getEpochSecond() / 60);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) redis.expire(key, Duration.ofMinutes(2));
            if (count == null || count > MAX_REQUESTS_PER_MINUTE) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "ASSISTANT_RATE_LIMIT", "Please wait a moment before asking another question.");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_RATE_LIMIT_UNAVAILABLE", "Assistant is temporarily unavailable. Please try again shortly.");
        }
    }
}
