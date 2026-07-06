package com.kazama.redis_cache_demo.seckill.service;

import com.kazama.common.snowflake.SnowflakeGenerator;
import com.kazama.redis_cache_demo.infra.cache.CacheResult;
import com.kazama.redis_cache_demo.order.entity.Orders;
import com.kazama.redis_cache_demo.order.exception.DuplicateOrderException;
import com.kazama.redis_cache_demo.order.exception.OrderDBUnavailableException;
import com.kazama.redis_cache_demo.order.service.OrderService;
import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.dto.SeckillRequest;
import com.kazama.redis_cache_demo.seckill.enums.Status;
import com.kazama.redis_cache_demo.seckill.exception.SeckillActivityNotFoundException;
import com.kazama.redis_cache_demo.seckill.exception.SeckillStockExhaustedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class SeckillServiceTest {

    @Mock private SeckillActivityCacheService seckillActivityCacheService;
    @Mock private SeckillActivityService seckillActivityService;
    @Mock private CircuitBreaker orderDBCircuitBreaker;
    @Mock private OrderService orderService;
    @Mock private SnowflakeGenerator snowflakeGenerator;

    private SeckillService seckillService;

    private static final Long ACTIVITY_ID = 1L;
    private static final Long USER_ID = 42L;
    private static final Long ORDER_ID = 99999L;

    @BeforeEach
    void setUp() {
        Retry retry = Retry.of("test", RetryConfig.custom().maxAttempts(1).build());
        seckillService = new SeckillService(
                seckillActivityCacheService,
                seckillActivityService,
                orderDBCircuitBreaker,
                retry,
                orderService,
                snowflakeGenerator
        );
    }

    // ------------------------------------------------------------------ helpers

    private SeckillActivityDTO activeActivity() {
        return new SeckillActivityDTO(
                ACTIVITY_ID, 100L,
                BigDecimal.valueOf(100), BigDecimal.valueOf(50),
                100, 50, 5,
                ZonedDateTime.now().minusHours(1),
                ZonedDateTime.now().plusHours(1),
                Status.ACTIVE
        );
    }

    private SeckillRequest request(int quantity) {
        return new SeckillRequest(ACTIVITY_ID, USER_ID, quantity);
    }

    private Orders savedOrder() {
        return Orders.builder().id(ORDER_ID).build();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void deductStock_cacheHit_happyPath_returnsOrderId() {
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.hit(activeActivity()));
        when(seckillActivityCacheService.deductStock(any())).thenReturn(49L);
        when(orderDBCircuitBreaker.tryAcquirePermission()).thenReturn(true);
        when(snowflakeGenerator.nextId()).thenReturn(ORDER_ID);
        when(orderService.createOrder(any())).thenReturn(savedOrder());

        Long result = seckillService.deductStock(request(1));

        assertEquals(ORDER_ID, result);
        verify(orderService).createOrder(any());
    }

    @Test
    void deductStock_cacheNullHit_throwsActivityNotFoundException() {
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.nullHit());

        assertThrows(SeckillActivityNotFoundException.class,
                () -> seckillService.deductStock(request(1)));

        verifyNoInteractions(orderService);
    }

    @Test
    void deductStock_cacheMiss_rewarmsAndReturnsOrderId() {
        SeckillActivityDTO dto = activeActivity();
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.miss());
        when(seckillActivityService.rewarming(ACTIVITY_ID)).thenReturn(dto);
        when(seckillActivityCacheService.deductStock(any())).thenReturn(49L);
        when(orderDBCircuitBreaker.tryAcquirePermission()).thenReturn(true);
        when(snowflakeGenerator.nextId()).thenReturn(ORDER_ID);
        when(orderService.createOrder(any())).thenReturn(savedOrder());

        Long result = seckillService.deductStock(request(1));

        assertEquals(ORDER_ID, result);
        verify(seckillActivityService).rewarming(ACTIVITY_ID);
    }

    @Test
    void deductStock_stockExhausted_throwsSeckillStockExhaustedException() {
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.hit(activeActivity()));
        when(seckillActivityCacheService.deductStock(any())).thenReturn(-3L);

        assertThrows(SeckillStockExhaustedException.class,
                () -> seckillService.deductStock(request(1)));

        verifyNoInteractions(orderService);
    }

    @Test
    void deductStock_duplicateOrder_throwsDuplicateOrderException() {
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.hit(activeActivity()));
        when(seckillActivityCacheService.deductStock(any())).thenReturn(-2L);

        assertThrows(DuplicateOrderException.class,
                () -> seckillService.deductStock(request(1)));

        verifyNoInteractions(orderService);
    }

    @Test
    void deductStock_activityKeyMissingInRedis_throwsActivityNotFoundException() {
        // Lua returns -1 when the stock key no longer exists (activity expired in Redis)
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.hit(activeActivity()));
        when(seckillActivityCacheService.deductStock(any())).thenReturn(-1L);

        assertThrows(SeckillActivityNotFoundException.class,
                () -> seckillService.deductStock(request(1)));

        verifyNoInteractions(orderService);
    }

    @Test
    void deductStock_activityNotYetStarted_throwsActivityNotFoundException() {
        SeckillActivityDTO notStarted = new SeckillActivityDTO(
                ACTIVITY_ID, 100L,
                BigDecimal.valueOf(100), BigDecimal.valueOf(50),
                100, 100, 5,
                ZonedDateTime.now().plusMinutes(30),   // starts in the future
                ZonedDateTime.now().plusHours(2),
                Status.PENDING
        );
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.hit(notStarted));

        assertThrows(SeckillActivityNotFoundException.class,
                () -> seckillService.deductStock(request(1)));

        verify(seckillActivityCacheService, never()).deductStock(any());
    }

    @Test
    void deductStock_activityAlreadyEnded_throwsActivityNotFoundException() {
        SeckillActivityDTO ended = new SeckillActivityDTO(
                ACTIVITY_ID, 100L,
                BigDecimal.valueOf(100), BigDecimal.valueOf(50),
                100, 0, 5,
                ZonedDateTime.now().minusHours(2),
                ZonedDateTime.now().minusHours(1),    // ended in the past
                Status.ENDED
        );
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.hit(ended));

        assertThrows(SeckillActivityNotFoundException.class,
                () -> seckillService.deductStock(request(1)));

        verify(seckillActivityCacheService, never()).deductStock(any());
    }

    @Test
    void deductStock_quantityExceedsMax_throwsIllegalArgumentException() {
        // maxQuantityPerOrder = 5, requesting 10
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.hit(activeActivity()));

        assertThrows(IllegalArgumentException.class,
                () -> seckillService.deductStock(request(10)));

        verify(seckillActivityCacheService, never()).deductStock(any());
    }

    @Test
    void deductStock_circuitBreakerOpen_throwsOrderDBUnavailableException() {
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.hit(activeActivity()));
        when(seckillActivityCacheService.deductStock(any())).thenReturn(49L);
        when(orderDBCircuitBreaker.tryAcquirePermission()).thenReturn(false);

        assertThrows(OrderDBUnavailableException.class,
                () -> seckillService.deductStock(request(1)));

        verifyNoInteractions(orderService);
    }

    @Test
    void deductStock_orderCreationFailsWithRetry_propagatesException() {
        when(seckillActivityCacheService.getActivity(ACTIVITY_ID))
                .thenReturn(CacheResult.hit(activeActivity()));
        when(seckillActivityCacheService.deductStock(any())).thenReturn(49L);
        when(orderDBCircuitBreaker.tryAcquirePermission()).thenReturn(true);
        when(snowflakeGenerator.nextId()).thenReturn(ORDER_ID);
        when(orderService.createOrder(any())).thenThrow(new RuntimeException("DB down"));

        assertThrows(RuntimeException.class,
                () -> seckillService.deductStock(request(1)));

        verify(orderDBCircuitBreaker).onError(anyLong(), any(), any());
    }
}
