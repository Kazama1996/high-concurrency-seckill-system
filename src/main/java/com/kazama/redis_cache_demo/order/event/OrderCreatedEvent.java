package com.kazama.redis_cache_demo.order.event;

import java.math.BigDecimal;

public record OrderCreatedEvent(
        Long id,
        Long seckillActivityId,
        Long productId,
        Long userId,
        Integer quantity,
        BigDecimal originalPrice,
        BigDecimal seckillPrice) {}
