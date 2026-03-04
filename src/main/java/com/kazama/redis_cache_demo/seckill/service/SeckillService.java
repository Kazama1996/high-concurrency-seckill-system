package com.kazama.redis_cache_demo.seckill.service;

import com.kazama.redis_cache_demo.infra.cache.CacheResult;
import com.kazama.redis_cache_demo.infra.cache.Status;
import com.kazama.redis_cache_demo.infra.ratelimit.RateLimit;
import com.kazama.redis_cache_demo.infra.ratelimit.RateLimitType;
import com.kazama.redis_cache_demo.order.exception.DuplicateOrderException;
import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.exception.SeckillActivityNotFoundException;
import com.kazama.redis_cache_demo.seckill.exception.SeckillStockExhaustedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeckillService {

    private final SeckillActivityCacheService seckillActivityCacheService;

    @RateLimit(key="'seckill:user:' + #userId + ':activity:' + #activityId" ,type = RateLimitType.SLIDING_WINDOW)
    public long deductStock(Long activityId , Long userId){
        log.debug("Start to  deduct seckill ");

        CacheResult<SeckillActivityDTO> activity = seckillActivityCacheService.getActivity(activityId);


        if (Status.NULL_HIT.equals(activity.status())) {
            throw new SeckillActivityNotFoundException("Seckill activity not found: " + activityId);
        }

        if (Status.MISS.equals(activity.status())) {
            throw new SeckillActivityNotFoundException("Seckill activity cache miss, pending re-warm: " + activityId);
        }

        ZonedDateTime now = ZonedDateTime.now();

        if(now.isAfter(activity.value().endTime()) || now.isBefore(activity.value().startTime())){
            throw new SeckillActivityNotFoundException("Seckill Activity is expire"+activityId);
        }

        long result = seckillActivityCacheService.deductStock(activityId,userId);




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

        return result;



    }
}
