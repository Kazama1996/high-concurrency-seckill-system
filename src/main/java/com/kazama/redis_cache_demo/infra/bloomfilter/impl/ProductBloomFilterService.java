package com.kazama.redis_cache_demo.infra.bloomfilter.impl;

import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import com.kazama.redis_cache_demo.infra.bloomfilter.AbstractBloomFilterService;
import com.kazama.redis_cache_demo.product.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ProductBloomFilterService extends AbstractBloomFilterService<Long> {

    private final ProductRepository productRepository;

    public ProductBloomFilterService(ProductRepository productRepository){
        this.productRepository = productRepository;
    }

    @Override
    protected String getFilterName() {
        return "bloom:product";
    }

    @Override
    protected long getExpectedInsertions() {
        return 500_00L;
    }

    @Override
    protected double getFalsePositiveRate() {
        return 0.001;
    }

    @Override
    protected Funnel<Long> getFunnel() {
        return Funnels.longFunnel();
    }

    @Override
    protected void loadAll() {
        List<Long> ids = productRepository.findAllIds();
        addAll(ids);
        log.info("[bloom:product] loaded {} product IDs", ids.size());
    }
}
