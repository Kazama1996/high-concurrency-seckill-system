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
     * Atomically checks-and-marks a notification as processed using Redis SET NX,
     * eliminating the check-then-set race that existed between separate
     * isAlreadyProcessed/markProcessed calls. Must be called only after the send
     * has actually succeeded — marking it before sending would block a legitimate
     * retry on redelivery.
     *
     * @return true if this call newly marked it; false if it was already marked
     *         (e.g. a concurrent duplicate delivery won the race) — the caller
     *         still acknowledges the message in both cases, since the send for
     *         this record has already completed either way.
     */
    public boolean tryMarkProcessed(Long orderId) {
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(PROCESSED_KEY_PREFIX + orderId, "1", PROCESSED_TTL);
        return Boolean.TRUE.equals(firstTime);
    }
}
