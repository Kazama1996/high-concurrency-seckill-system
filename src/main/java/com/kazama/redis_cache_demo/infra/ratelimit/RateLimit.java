package com.kazama.redis_cache_demo.infra.ratelimit;

import jakarta.persistence.Table;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    int limit() default 5;
    int window() default 60;
    String key();
    RateLimitType type() default RateLimitType.SLIDING_WINDOW;
}
