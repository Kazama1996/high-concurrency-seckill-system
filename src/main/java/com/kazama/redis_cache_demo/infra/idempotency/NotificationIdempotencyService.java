package com.kazama.redis_cache_demo.infra.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationIdempotencyService {

    private final StringRedisTemplate redisTemplate;

    private static final String PROCESSED_KEY_PREFIX = "seckill:notification:processed:";
    private static final Duration PROCESSED_TTL = Duration.ofHours(24);

    /**
     * Atomically claims processing rights for an order's notification.
     * @return true if this call won the claim (not yet processed), false if it was already processed.
     */
    public boolean tryMarkProcessed(Long orderId) {
        String key = PROCESSED_KEY_PREFIX + orderId;
        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(key, "1", PROCESSED_TTL);
        return Boolean.TRUE.equals(claimed);
    }
}
