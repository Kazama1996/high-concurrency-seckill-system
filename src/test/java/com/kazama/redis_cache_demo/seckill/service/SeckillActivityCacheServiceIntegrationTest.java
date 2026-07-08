package com.kazama.redis_cache_demo.seckill.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kazama.redis_cache_demo.AbstractRedisIntegrationTest;
import com.kazama.redis_cache_demo.seckill.dto.SeckillRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class SeckillActivityCacheServiceIntegrationTest extends AbstractRedisIntegrationTest {

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String ORDERS_KEY_PREFIX = "seckill:orders:";

    private static final AtomicLong ACTIVITY_ID_SEQ = new AtomicLong(9_000_000);

    private static StringRedisTemplate redisTemplate;
    private static SeckillActivityCacheService cacheService;

    private Long activityId;

    @BeforeAll
    static void setUpRedisClient() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost(), redisPort());
        config.setPassword(REDIS_TEST_PASSWORD);

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        cacheService = new SeckillActivityCacheService(redisTemplate, new ObjectMapper());
    }

    @BeforeEach
    void setUp() {
        activityId = ACTIVITY_ID_SEQ.getAndIncrement();
    }

    private String stockKey() {
        return STOCK_KEY_PREFIX + activityId;
    }

    private String ordersKey() {
        return ORDERS_KEY_PREFIX + activityId;
    }

    private SeckillRequest request(long userId, int quantity) {
        return new SeckillRequest(activityId, userId, quantity);
    }

    @Test
    void missingStockKey_returnsActivityNotFoundCode() {
        long result = cacheService.deductStock(request(1L, 1));

        assertThat(result).isEqualTo(-1L);
    }

    @Test
    void zeroStock_returnsStockExhaustedCode() {
        cacheService.reset(activityId, 0, 60);

        long result = cacheService.deductStock(request(1L, 1));

        assertThat(result).isEqualTo(-3L);
    }

    @Test
    void quantityExceedsStock_returnsStockExhaustedCode() {
        cacheService.reset(activityId, 5, 60);

        long result = cacheService.deductStock(request(1L, 10));

        assertThat(result).isEqualTo(-3L);
        assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("5");
    }

    @Test
    void orderingOfChecks_userAlreadyOrdered_butStockNowInsufficient_returnsStockExhaustedNotDuplicate() {
        cacheService.reset(activityId, 5, 60);
        assertThat(cacheService.deductStock(request(1L, 5))).isEqualTo(0L);

        // user 1 is already in the orders set, but the script must fail on the stock check first
        long result = cacheService.deductStock(request(1L, 1));

        assertThat(result).isEqualTo(-3L);
    }

    @Test
    void duplicateOrder_secondCallSameUser_returnsDuplicateCodeAndLeavesStateUnchanged() {
        cacheService.reset(activityId, 10, 60);

        assertThat(cacheService.deductStock(request(1L, 3))).isEqualTo(7L);

        long second = cacheService.deductStock(request(1L, 3));

        assertThat(second).isEqualTo(-2L);
        assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("7");
        assertThat(redisTemplate.opsForSet().members(ordersKey())).containsExactly("1");
    }

    @Test
    void sequentialCalls_decrementRemainderAcrossUsers() {
        cacheService.reset(activityId, 10, 60);

        assertThat(cacheService.deductStock(request(1L, 3))).isEqualTo(7L);
        assertThat(cacheService.deductStock(request(2L, 3))).isEqualTo(4L);
        assertThat(cacheService.deductStock(request(3L, 3))).isEqualTo(1L);
        assertThat(cacheService.deductStock(request(4L, 3))).isEqualTo(-3L);
    }

    @Test
    void exactZeroBoundary_stockEqualsQuantity_returnsZero() {
        cacheService.reset(activityId, 5, 60);

        long result = cacheService.deductStock(request(1L, 5));

        assertThat(result).isEqualTo(0L);
        assertThat(redisTemplate.opsForValue().get(stockKey())).isEqualTo("0");
    }
}
