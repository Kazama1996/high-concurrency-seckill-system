package com.kazama.redis_cache_demo.seckill.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kazama.redis_cache_demo.infra.cache.CacheResult;
import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.entity.SeckillActivity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeckillActivityCacheService {

    private  final StringRedisTemplate redisTemplate;
    private  final ObjectMapper objectMapper;

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    private static final String ACTIVITY_KEY_PREFIX = "seckill:activity:";
    private static final String NULL_CACHE_VALUE =  "NULL";
    private static final long NULL_CACHE_TTL = 120;


    private String buildStockKey(Long activityId){
        return STOCK_KEY_PREFIX+activityId;
    }
    private String buildActivityKey(Long activityId){
        return ACTIVITY_KEY_PREFIX+activityId;
    }


    public void setActivity(Long activityId , SeckillActivityDTO activity, long ttl){
        String key = buildActivityKey(activityId);
        try{
            String json = objectMapper.writeValueAsString(activity);
            redisTemplate.opsForValue().set(key,json , Duration.ofSeconds(ttl));
        }catch (JsonProcessingException e){
            log.error("Serialize failed:{}" , key, e);
        }
    }

    public void setStock(Long activityId , long stock, long ttl){
        String key = buildStockKey(activityId);
        redisTemplate.opsForValue().set(key,String.valueOf(stock) , Duration.ofSeconds(ttl));

    }

    public CacheResult<SeckillActivityDTO> getActivity(Long activityId){
        String key = buildActivityKey(activityId);
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
            SeckillActivityDTO dto = objectMapper.readValue(json,SeckillActivityDTO.class);
            log.debug("cache hit: {}" , key);
            return CacheResult.hit(dto);
        }catch (JsonProcessingException e){
            log.error("Deserialize failed : {}" , key , e);
            redisTemplate.delete(key);
            return CacheResult.miss();
        }
    }

    public CacheResult<Long> getStock(Long activityId){
        String key = buildStockKey(activityId);
        String json = redisTemplate.opsForValue().get(key);


        if(json==null){
            log.debug("cache is miss :{}" , key);
            return CacheResult.miss();
        }

        if(NULL_CACHE_VALUE.equals(json)){
            log.debug("cache hits NULL value {}" , key);
            return CacheResult.nullHit();
        }
        Long stock = Long.parseLong(json);
        log.debug("cache hit: {}" , key);
        return CacheResult.hit(stock);

    }


    public void setNullActivity(Long activityId){
        String key = buildActivityKey(activityId);
        redisTemplate.opsForValue().set(key, NULL_CACHE_VALUE, Duration.ofSeconds(NULL_CACHE_TTL));
        log.debug("WRITE NULL VALUE: {} , TTL:{}" , key , NULL_CACHE_TTL);
    }

    public void setNullStock(Long activityId){
        String key = buildStockKey(activityId);
        redisTemplate.opsForValue().set(key, NULL_CACHE_VALUE, Duration.ofSeconds(NULL_CACHE_TTL));
        log.debug("WRITE NULL VALUE: {} , TTL:{}" , key , NULL_CACHE_TTL);
    }

    public void deleteActivity(Long activityId) {
        redisTemplate.delete(buildActivityKey(activityId));
    }

    public void deleteStock(Long activityId) {
        redisTemplate.delete(buildStockKey(activityId));
    }




}
