package com.kazama.redis_cache_demo.infra.ratelimit.impl;

import com.kazama.redis_cache_demo.infra.ratelimit.RateLimitType;
import com.kazama.redis_cache_demo.infra.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limig - tonumber(ARGV[3])
            local expireTime = now - window * 1000
            redis.call('ZREMRANGEBYSCORE',key,0,expireTime)
            local count = redis.call('ZCARD',key)
            if count >= limit then
                return 0
            end
            redis.call('ZADD',key,now,now)
            redis.call('EXPIRE',key,window)
            return 1
            """;

    private final RedisScript<Long> script = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT);

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
