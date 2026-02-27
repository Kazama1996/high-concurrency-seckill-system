package com.kazama.redis_cache_demo.infra.ratelimit;

public interface RateLimiter {

    boolean isAllowed(String key , int limit , int windowSeconds);

    RateLimitType supportedType();
}
