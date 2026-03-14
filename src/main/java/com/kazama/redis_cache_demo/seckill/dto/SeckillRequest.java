package com.kazama.redis_cache_demo.seckill.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SeckillRequest(
        @NotNull Long activityId,
        @NotNull Long userId,
        @NotNull @Min(1) Integer quantity) {}
