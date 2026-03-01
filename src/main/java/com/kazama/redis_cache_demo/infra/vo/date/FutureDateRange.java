package com.kazama.redis_cache_demo.infra.vo.date;

import com.kazama.redis_cache_demo.infra.validation.annotation.ValidateDateRange;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

@ValidateDateRange
public record FutureDateRange(
        @NotNull @Future ZonedDateTime startTime,
        @NotNull @Future ZonedDateTime endTime
)implements DateRangeValidatable {}
