package com.kazama.redis_cache_demo.seckill.exception;

public class SeckillActivityOverlappingException extends RuntimeException {
    public SeckillActivityOverlappingException(String message) {
        super(message);
    }
}
