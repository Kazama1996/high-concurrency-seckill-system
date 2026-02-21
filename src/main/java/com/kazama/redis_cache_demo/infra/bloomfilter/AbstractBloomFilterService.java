package com.kazama.redis_cache_demo.infra.bloomfilter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

@Slf4j
public abstract  class AbstractBloomFilterService<T> {

    private final RedissonClient redissonClient;

    private RBloomFilter<T> bloomFilter;

    protected AbstractBloomFilterService(RedissonClient redissonClient){
        this.redissonClient = redissonClient;
    }


    protected  abstract  String getFilterName();
    protected  abstract  long getExpectedInsertions();
    protected  abstract  double getFalsePositiveRate();
    protected  abstract  void loadAll();


    @PostConstruct
    public void init(){
        log.info("[{}] initializing bloom filter...", getFilterName());

        bloomFilter = redissonClient.getBloomFilter(getFilterName());

        if(bloomFilter.isExists()){
            log.info("[{}] already exists, current count: {}", getFilterName(), bloomFilter.count());
            return;
        }

        boolean initialized = bloomFilter.tryInit(getExpectedInsertions() , getFalsePositiveRate());
        if(!initialized){
            log.warn("[{}] init failed, filter already exists", getFilterName());
            return;
        }

        log.info("[{}] init success, capacity: {} , fpr: {}", getFilterName() , getExpectedInsertions() , getFalsePositiveRate());
        loadAll();

    }

    public boolean mightContain(T id){
        if(id ==null) return false;

        boolean result = bloomFilter.contains(id);
        log.debug("[{}] check: {}, result: {}", getFilterName(), id, result);
        return result;
    }

    public void add(T id){
        if(id==null) return;
        bloomFilter.add(id);
        log.debug("[{}] added: {}", getFilterName(), id);
    }


    public void addAll(Iterable<T> ids) {
        if (ids == null) return;
        int count = 0;
        for (T id : ids) {
            if (id != null) {
                bloomFilter.add(id);
                count++;
            }
        }
        log.debug("[{}] batch added: {} items", getFilterName(), count);
    }


    public void rebuild() {
        log.info("[{}] rebuilding...", getFilterName());
        bloomFilter.delete();
        bloomFilter.tryInit(getExpectedInsertions(), getFalsePositiveRate());
        loadAll();
        log.info("[{}] rebuild complete", getFilterName());
    }




}
