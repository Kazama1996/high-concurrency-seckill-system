package com.kazama.redis_cache_demo.infra.outbox.enums;

public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED,
}
