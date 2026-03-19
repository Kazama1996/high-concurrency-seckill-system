package com.kazama.redis_cache_demo.infra.exception;

public class KafkaUnavailableException extends ServiceUnavailableException {
    public KafkaUnavailableException(String message) {
        super(message);
    }
}
