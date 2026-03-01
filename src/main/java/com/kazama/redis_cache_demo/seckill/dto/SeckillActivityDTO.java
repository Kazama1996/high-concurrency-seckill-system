package com.kazama.redis_cache_demo.seckill.dto;

import com.kazama.redis_cache_demo.seckill.enums.Status;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record SeckillActivityDTO(
        Long id,
        Long productId,
        BigDecimal seckillPrice,
        Integer totalStock,
        Integer remainingStock,
        ZonedDateTime startTime,
        ZonedDateTime endTime,
        Status status) {
}
