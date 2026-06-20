package com.kazama.redis_cache_demo.infra.util.impl;

import com.kazama.redis_cache_demo.infra.util.RandomGenerator;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class ThreadLocalRandomGenerator implements RandomGenerator {
    @Override
    public long nextLong(long bound) {
        return ThreadLocalRandom.current().nextLong(bound);
    }
}
