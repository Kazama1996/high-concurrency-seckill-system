package com.kazama.redis_cache_demo.infra.idempotency;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class NotificationIdempotencyServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks private NotificationIdempotencyService idempotencyService;

    private static final Long ORDER_ID = 42L;
    private static final String EXPECTED_KEY = "seckill:notification:processed:" + ORDER_ID;

    @Test
    void isAlreadyProcessed_keyExists_returnsTrue() {
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenReturn(Boolean.TRUE);

        assertTrue(idempotencyService.isAlreadyProcessed(ORDER_ID));
        verify(redisTemplate).hasKey(EXPECTED_KEY);
    }

    @Test
    void isAlreadyProcessed_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenReturn(Boolean.FALSE);

        assertFalse(idempotencyService.isAlreadyProcessed(ORDER_ID));
    }

    @Test
    void isAlreadyProcessed_hasKeyReturnsNull_returnsFalse() {
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenReturn(null);

        assertFalse(idempotencyService.isAlreadyProcessed(ORDER_ID));
    }

    @Test
    void tryMarkProcessed_setsKeyWithExpectedValueAndTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        idempotencyService.tryMarkProcessed(ORDER_ID);

        verify(valueOperations).setIfAbsent(EXPECTED_KEY, "1", Duration.ofHours(24));
    }
}
