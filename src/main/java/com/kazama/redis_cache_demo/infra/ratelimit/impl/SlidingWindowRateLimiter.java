package com.kazama.redis_cache_demo.infra.ratelimit.impl;

import com.kazama.redis_cache_demo.infra.ratelimit.RateLimitType;
import com.kazama.redis_cache_demo.infra.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class SlidingWindowRateLimiter implements RateLimiter {


    private final StringRedisTemplate redisTemplate;

    private static final String SLIDING_WINDOW_SCRIPT = """
            
            """;

    private final RedisScript<Long> script = RedisScript.of(new ClassPathResource("lua/ratelimit/sliding_window_ratelimiter.lua"), Long.class);

    @Override
    public boolean isAllowed(String key, int limit, int windowSeconds) {


        long now = System.currentTimeMillis();

        Long result = redisTemplate.execute(script,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(windowSeconds),
                String.valueOf(limit));



        log.debug("SlidingWindow key: {}, result: {}", key, result);
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public RateLimitType supportedType() {
        return RateLimitType.SLIDING_WINDOW;
    }
}
