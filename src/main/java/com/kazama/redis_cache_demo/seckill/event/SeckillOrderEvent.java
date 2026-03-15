package com.kazama.redis_cache_demo.seckill.event;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record SeckillOrderEvent(
        Long activityId,
        Long productId,
        Long userId,
        Integer quantity,
        BigDecimal originalPrice,
        BigDecimal seckillPrice){}
