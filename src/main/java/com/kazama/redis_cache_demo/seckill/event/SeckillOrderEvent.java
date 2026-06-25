package com.kazama.redis_cache_demo.seckill.event;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record SeckillOrderEvent(
        Long id,
        Long seckillActivityId,
        Long productId,
        Long userId,
        Integer quantity,
        BigDecimal originalPrice,
        BigDecimal seckillPrice){}
