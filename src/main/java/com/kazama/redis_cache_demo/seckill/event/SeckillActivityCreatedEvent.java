package com.kazama.redis_cache_demo.seckill.event;

import java.time.ZonedDateTime;

public record SeckillActivityCreatedEvent(Long activityId , Long productId , ZonedDateTime startTime , ZonedDateTime endTime) {
}
