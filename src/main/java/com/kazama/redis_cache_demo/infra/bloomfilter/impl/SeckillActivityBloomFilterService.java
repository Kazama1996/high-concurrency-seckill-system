package com.kazama.redis_cache_demo.infra.bloomfilter.impl;

import com.kazama.redis_cache_demo.infra.bloomfilter.AbstractBloomFilterService;
import com.kazama.redis_cache_demo.seckill.repository.SeckillActivityRepository;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class SeckillActivityBloomFilterService extends AbstractBloomFilterService<Long> {


    private final SeckillActivityRepository seckillActivityRepository;

    public SeckillActivityBloomFilterService(RedissonClient redissonClient , SeckillActivityRepository seckillActivityRepository){
        super(redissonClient);
        this.seckillActivityRepository = seckillActivityRepository;
    }

    @Override
    protected String getFilterName() {
        return "bloom:seckill:activity";
    }

    @Override
    protected long getExpectedInsertions() {
        return 500_00L;
    }

    @Override
    protected double getFalsePositiveRate() {
        return 0.0;
    }

    @Override
    protected void loadAll() {
        List<Long> ids = seckillActivityRepository.findAllIds();
        addAll(ids);
        log.info("[bloom:seckill:activity] loaded {} product IDs", ids.size());
    }
}
