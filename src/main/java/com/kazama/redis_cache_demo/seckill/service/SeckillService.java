package com.kazama.redis_cache_demo.seckill.service;

import com.kazama.redis_cache_demo.infra.cache.CacheResult;
import com.kazama.redis_cache_demo.infra.cache.Status;
import com.kazama.redis_cache_demo.infra.circuitbreaker.annotation.RedisCircuitBreaker;
import com.kazama.redis_cache_demo.infra.ratelimit.RateLimit;
import com.kazama.redis_cache_demo.infra.ratelimit.RateLimitType;
import com.kazama.redis_cache_demo.order.exception.DuplicateOrderException;
import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.dto.SeckillRequest;
import com.kazama.redis_cache_demo.seckill.event.SeckillOrderEvent;
import com.kazama.redis_cache_demo.seckill.exception.SeckillActivityNotFoundException;
import com.kazama.redis_cache_demo.seckill.exception.SeckillStockExhaustedException;
import com.kazama.redis_cache_demo.seckill.kafka.producer.SeckillOrderProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillActivityCacheService seckillActivityCacheService;

    private final SeckillActivityService seckillActivityService;

    private final SeckillOrderProducer seckillOrderProducer;


    public long deductStock(SeckillRequest request) {
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


        log.info("Seckill success, activityId: {}, userId: {}, remaining: {}", activityId, userId, result);

        seckillOrderProducer.publishMessage(new SeckillOrderEvent(dto.id(),dto.productId(),userId,quantity,dto.originalPrice() , dto.seckillPrice()));

        return result;



    }
}
