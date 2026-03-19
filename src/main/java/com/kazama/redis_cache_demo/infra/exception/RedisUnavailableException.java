package com.kazama.redis_cache_demo.infra.exception;

public class RedisUnavailableException extends ServiceUnavailableException {
    public RedisUnavailableException(String message) {
        super(message);
    }
}
