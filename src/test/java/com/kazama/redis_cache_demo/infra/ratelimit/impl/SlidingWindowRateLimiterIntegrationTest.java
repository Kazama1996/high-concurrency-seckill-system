package com.kazama.redis_cache_demo.infra.ratelimit.impl;

import com.kazama.redis_cache_demo.AbstractRedisIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SlidingWindowRateLimiterIntegrationTest extends AbstractRedisIntegrationTest {

    private static final AtomicLong KEY_SEQ = new AtomicLong(9_000_000);

    private static StringRedisTemplate redisTemplate;
    private static SlidingWindowRateLimiter rateLimiter;

    private String key;

    @BeforeAll
    static void setUpRedisClient() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost(), redisPort());
        config.setPassword(REDIS_TEST_PASSWORD);

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        rateLimiter = new SlidingWindowRateLimiter(redisTemplate);
    }

    @BeforeEach
    void setUp() {
        key = "ratelimit:test:" + KEY_SEQ.getAndIncrement();
    }

    // A generously long window (well beyond any realistic time these few sequential Redis round
    // trips could take, even under a slow/loaded CI runner) so these admission/boundary tests
    // never flake due to the sliding window genuinely expiring an entry mid-test. Only
    // windowExpiry_allowsRequestsAgainAfterWindowPasses below intentionally uses a short window.
    private static final int NON_EXPIRING_WINDOW_SECONDS = 60;

    @Test
    void admitsRequestsUpToLimit() {
        assertThat(rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS)).isTrue();
        assertThat(rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS)).isTrue();
        assertThat(rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS)).isTrue();
    }

    @Test
    void rejectsRequestOverLimit() {
        rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS);
        rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS);
        rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS);

        boolean fourth = rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS);

        assertThat(fourth).isFalse();
    }

    @Test
    void rejectedCall_isNotAddedToTheWindow() {
        rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS);
        rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS);
        rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS);
        rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS); // rejected, should not grow the sorted set

        Long count = redisTemplate.opsForZSet().size(key);

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void allowedCall_setsExpiryOnKey() {
        rateLimiter.isAllowed(key, 3, NON_EXPIRING_WINDOW_SECONDS);

        Long ttl = redisTemplate.getExpire(key, SECONDS);

        assertThat(ttl).isGreaterThan(0L);
    }

    @Test
    void windowExpiry_allowsRequestsAgainAfterWindowPasses() {
        rateLimiter.isAllowed(key, 2, 1);
        rateLimiter.isAllowed(key, 2, 1);
        assertThat(rateLimiter.isAllowed(key, 2, 1)).isFalse();

        await().atMost(3, SECONDS).untilAsserted(() ->
                assertThat(rateLimiter.isAllowed(key, 2, 1)).isTrue());
    }

    // Best-effort, non-deterministic: two calls landing on the exact same millisecond would
    // collide in the sorted set (member == score == now), since ZADD on an existing member just
    // updates its score instead of adding a second entry. Forcing an exact same-millisecond
    // collision isn't reliably reproducible in a JUnit test, so this only asserts the invariant
    // that the limit is never exceeded across a rapid-fire burst -- it does not prove a collision
    // actually occurred on any given run.
    @Test
    void rapidFireCalls_neverAdmitMoreThanLimit() {
        int limit = 5;
        int allowedCount = 0;
        for (int i = 0; i < 50; i++) {
            if (rateLimiter.isAllowed(key, limit, NON_EXPIRING_WINDOW_SECONDS)) {
                allowedCount++;
            }
        }

        assertThat(allowedCount).isLessThanOrEqualTo(limit);
    }
}
