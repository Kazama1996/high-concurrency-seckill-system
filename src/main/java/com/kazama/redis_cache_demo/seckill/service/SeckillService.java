package com.kazama.redis_cache_demo.seckill.service;

import com.kazama.common.snowflake.SnowflakeGenerator;
import com.kazama.redis_cache_demo.infra.cache.CacheResult;
import com.kazama.redis_cache_demo.infra.cache.Status;
import com.kazama.redis_cache_demo.order.entity.Orders;
import com.kazama.redis_cache_demo.order.enums.OrderStatus;
import com.kazama.redis_cache_demo.order.exception.DuplicateOrderException;
import com.kazama.redis_cache_demo.order.exception.OrderDBUnavailableException;
import com.kazama.redis_cache_demo.order.service.OrderService;
import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.dto.SeckillRequest;
import com.kazama.redis_cache_demo.seckill.exception.SeckillActivityNotFoundException;
import com.kazama.redis_cache_demo.seckill.exception.SeckillStockExhaustedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillActivityCacheService seckillActivityCacheService;

    private final SeckillActivityService seckillActivityService;

    @Qualifier("orderDBCircuitBreaker")
    private final CircuitBreaker orderDBCircuitBreaker;

    @Qualifier("orderCreationRetry")
    private final Retry orderCreationRetry;

    private final OrderService orderService;

    private final SnowflakeGenerator snowflakeGenerator;



    public Long deductStock(SeckillRequest request) {
        log.debug("Start to  deduct seckill ");

        final Long activityId = request.activityId();
        final Long userId = request.userId();
        final Integer quantity = request.quantity();

        CacheResult<SeckillActivityDTO> activity = seckillActivityCacheService.getActivity(activityId);


        if (Status.NULL_HIT.equals(activity.status())) {
            throw new SeckillActivityNotFoundException("Seckill activity not found: " + activityId);
        }

        SeckillActivityDTO dto;

        if (Status.MISS.equals(activity.status())) {
            dto = seckillActivityService.rewarming(activityId);
        } else {
            dto = activity.value();
        }

        if (quantity < 1 || quantity > dto.maxQuantityPerOrder()) {
            throw new IllegalArgumentException(
                    "Invalid quantity: " + quantity + ", max allowed: " + dto.maxQuantityPerOrder()
            );
        }

        ZonedDateTime now = ZonedDateTime.now();

        if(now.isAfter(dto.endTime()) || now.isBefore(dto.startTime())){
            throw new SeckillActivityNotFoundException("Seckill Activity is expire"+activityId);
        }

        long result = seckillActivityCacheService.deductStock(request);




        if (result == -1L) {
            throw new SeckillActivityNotFoundException("Activity expired: " + activityId);
        }

        if(result == -2L){
            throw new DuplicateOrderException("Order is duplicate for activity " + activityId + ", userId: " + userId);
        }

        if (result == 0L) {
            throw new SeckillStockExhaustedException("Stock exhausted for activity: " + activityId);
        }



        if(!orderDBCircuitBreaker.tryAcquirePermission()){
            throw new OrderDBUnavailableException("Order db circuit breaker is open");
        }

        long start = System.currentTimeMillis();


        Orders orders = Orders
                .builder()
                .id(snowflakeGenerator.nextId())
                .userId(userId)
                .productId(dto.productId())
                .seckillActivityId(dto.id())
                .quantity(quantity)
                .originalPrice(dto.originalPrice())
                .seckillPrice(dto.seckillPrice())
                .orderStatus(OrderStatus.UNPAID)
                .build();
        Supplier<Orders> supplier = Retry.decorateSupplier(orderCreationRetry , ()->orderService.createOrder(orders));


        try{
            Orders res = supplier.get();
            long duration = System.currentTimeMillis() - start;
            orderDBCircuitBreaker.onSuccess(duration, TimeUnit.MILLISECONDS);

            log.info("Seckill success, activityId: {}, userId: {}, remaining: {}", activityId, userId, result);
            return res.getId();
        }catch (Exception e){
            long duration = System.currentTimeMillis() - start;
            orderDBCircuitBreaker.onError(duration , TimeUnit.MILLISECONDS , e);
            throw e;
        }


    }
}
