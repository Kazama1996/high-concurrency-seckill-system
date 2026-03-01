package com.kazama.redis_cache_demo.seckill.dto;

import com.kazama.redis_cache_demo.infra.vo.date.DateRange;
import com.kazama.redis_cache_demo.infra.vo.date.FutureDateRange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateSeckillActivityRequest(
        @NotNull  Long productId,
        @NotNull @Min(1) BigDecimal seckillPrice,
        @NotNull @Min(1) Integer totalStock,
        @NotNull @Valid FutureDateRange dateRange
) {}
