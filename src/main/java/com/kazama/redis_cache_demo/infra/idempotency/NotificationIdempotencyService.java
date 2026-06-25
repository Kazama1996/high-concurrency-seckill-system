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

    public boolean isAlreadyProcessed(Long orderId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PROCESSED_KEY_PREFIX + orderId));
    }

    /**
     * Records a notification as processed. Call only after the send has actually succeeded —
     * marking it before sending would block a legitimate retry on redelivery.
     */
    public void markProcessed(Long orderId) {
        redisTemplate.opsForValue().set(PROCESSED_KEY_PREFIX + orderId, "1", PROCESSED_TTL);
    }
}
