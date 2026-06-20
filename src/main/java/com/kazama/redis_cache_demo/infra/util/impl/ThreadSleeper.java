package com.kazama.redis_cache_demo.infra.util.impl;

import com.kazama.redis_cache_demo.infra.util.Sleeper;
import org.springframework.stereotype.Component;

@Component
public class ThreadSleeper implements Sleeper {
    @Override
    public void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}
