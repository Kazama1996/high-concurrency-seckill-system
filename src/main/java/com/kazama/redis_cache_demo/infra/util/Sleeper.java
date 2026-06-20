package com.kazama.redis_cache_demo.infra.util;

public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
