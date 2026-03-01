package com.kazama.redis_cache_demo.infra.vo.date;

import com.kazama.redis_cache_demo.infra.validation.annotation.ValidateDateRange;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

@ValidateDateRange
public record DateRange(
        @NotNull ZonedDateTime startTime,
        @NotNull  ZonedDateTime endTime
)implements DateRangeValidatable {}
