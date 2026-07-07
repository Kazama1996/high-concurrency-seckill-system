package com.kazama.redis_cache_demo.infra.lock.impl;

import com.kazama.redis_cache_demo.infra.lock.DistributeLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;


@Slf4j
@RequiredArgsConstructor
@Service
public class DistributeLockServiceImpl implements DistributeLockService {

    private final RedissonClient redissonClient;


    @Override
    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }
}
