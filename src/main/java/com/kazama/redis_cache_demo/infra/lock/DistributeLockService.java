package com.kazama.redis_cache_demo.infra.lock;

import org.redisson.api.RLock;

public interface DistributeLockService {

    RLock getLock(String lockKey);
}
