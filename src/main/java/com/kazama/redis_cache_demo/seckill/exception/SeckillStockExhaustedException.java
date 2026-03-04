package com.kazama.redis_cache_demo.seckill.exception;

public class SeckillStockExhaustedException extends RuntimeException {
    public SeckillStockExhaustedException(String message) {
        super(message);
    }
}
