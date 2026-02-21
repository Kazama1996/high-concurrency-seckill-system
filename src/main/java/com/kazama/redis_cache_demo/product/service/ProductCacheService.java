package com.kazama.redis_cache_demo.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kazama.redis_cache_demo.infra.cache.CacheResult;
import com.kazama.redis_cache_demo.infra.cache.Status;
import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCacheService {


    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;



    private static final String CACHE_KEY_PREFIX = "product:detail:";
    private static final String NULL_CACHE_VALUE = "NULL";


    private static final long BASE_TTL = 3600;
    private static final long RANDOM_TTL_RANGE= 300;
    private static final long NULL_CACHE_TTL = 120;

    private String buildKey(Long productId){
        return CACHE_KEY_PREFIX+productId;
    }

    public CacheResult<ProductDTO> get(Long productId){
        String key = buildKey(productId);
        String json = redisTemplate.opsForValue().get(key);

        if(json==null){
            log.debug("cache is miss :{}" , key);
            return CacheResult.miss();
        }

        if(NULL_CACHE_VALUE.equals(json)){
            log.debug("cache hits NULL value {}" , key);
            return CacheResult.nullHit();

        }

        try{
            ProductDTO dto = objectMapper.readValue(json , ProductDTO.class);
            log.debug("cache hit: {}" , key);
            return CacheResult.hit(dto);

        }catch (JsonProcessingException e){
            log.error("Deserialize failed : {}" , key , e);
            redisTemplate.delete(key);
            return CacheResult.miss();
        }
    }

    public void set(Long productId , ProductDTO product){
        String key = buildKey(productId);

        try{
            String json = objectMapper.writeValueAsString(product);
            long ttl = calculateRandomTTL();

            redisTemplate.opsForValue().set(key , json , Duration.ofSeconds(ttl));
            log.debug("write cache: {} , TTL:{} seconds", key, ttl);
        }catch (JsonProcessingException e){
            log.error("Serialize failed:{}" , key, e);
        }
    }



    public void setNull(Long productId){
        String key = buildKey(productId);
        redisTemplate.opsForValue().set(key , NULL_CACHE_VALUE,Duration.ofSeconds(NULL_CACHE_TTL));
        log.debug("WRITE NULL VALUE: {} , TTL:{}" , key , NULL_CACHE_TTL);
    }

    public void delete(Long productId){
        String key = buildKey(productId);
        redisTemplate.delete(key);
        log.debug("DELETE cache: {}" , key);
    }

    public void batchDelete(Long ...productIds){
        if(productIds==null || productIds.length==0){
            return;
        }

        for(Long productId : productIds){
            delete(productId);
        }
    }

    public boolean exists(Long productId){
        String key = buildKey(productId);
        return redisTemplate.hasKey(key);
    }



    private long calculateRandomTTL(){
        long randomSeconds = ThreadLocalRandom.current().nextLong(0,RANDOM_TTL_RANGE);
        return BASE_TTL+ randomSeconds;
    }
}
