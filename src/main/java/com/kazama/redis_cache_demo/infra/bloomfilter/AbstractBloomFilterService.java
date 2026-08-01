package com.kazama.redis_cache_demo.infra.bloomfilter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnel;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

@Slf4j
public abstract  class AbstractBloomFilterService<T> {

    private volatile BloomFilter<T> bloomFilter;
    // volatile: rebuild() 會整顆替換掉物件參照,要保證多執行緒下的可見性

    protected abstract String getFilterName();
    protected abstract long getExpectedInsertions();
    protected abstract double getFalsePositiveRate();
    protected abstract Funnel<T> getFunnel();
    protected abstract void loadAll();

    @PostConstruct
    public void init() {
        log.info("[{}] initializing in-memory bloom filter...", getFilterName());

        bloomFilter = BloomFilter.create(
                getFunnel(),
                getExpectedInsertions(),
                getFalsePositiveRate()
        );

        loadAll();

        log.info("[{}] init success, capacity: {}, fpr: {}",
                getFilterName(), getExpectedInsertions(), getFalsePositiveRate());
    }

    public boolean mightContain(T id) {
        if (id == null) return false;

        boolean result = bloomFilter.mightContain(id);
        log.debug("[{}] check: {}, result: {}", getFilterName(), id, result);
        return result;
    }

    public void add(T id) {
        if (id == null) return;
        bloomFilter.put(id);
        log.debug("[{}] added: {}", getFilterName(), id);
    }

    public void addAll(Iterable<T> ids) {
        if (ids == null) return;
        int count = 0;
        for (T id : ids) {
            if (id != null) {
                bloomFilter.put(id);
                count++;
            }
        }
        log.debug("[{}] batch added: {} items", getFilterName(), count);
    }

    public void rebuild() {
        log.info("[{}] rebuilding...", getFilterName());

        bloomFilter = BloomFilter.create(
                getFunnel(),
                getExpectedInsertions(),
                getFalsePositiveRate()
        );

        loadAll();
        log.info("[{}] rebuild complete", getFilterName());
    }










}
