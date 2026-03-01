package com.kazama.redis_cache_demo.infra.vo.date;

import java.time.ZonedDateTime;

public interface DateRangeValidatable {
    ZonedDateTime startTime();
    ZonedDateTime endTime();
}
