package com.kazama.redis_cache_demo.order.exception;

public class OrderDBUnavailableException extends RuntimeException {
    public OrderDBUnavailableException(String message) {
        super(message);
    }
}
