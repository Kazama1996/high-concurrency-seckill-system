package com.kazama.redis_cache_demo.product.service;

import com.kazama.redis_cache_demo.infra.cache.BloomFilterService;
import com.kazama.redis_cache_demo.infra.lock.DistributeLockService;
import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.product.entity.Product;
import com.kazama.redis_cache_demo.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.CircuitBreaker;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCacheService productCacheService;
    private final BloomFilterService bloomFilterService;
    private final DistributeLockService lockService;

    private final CircuitBreaker circuitBreaker;

    private static final long LOCK_WAIT_TIME = 3;  // 等待鎖的最大時間（秒）
    private static final long LOCK_LEASE_TIME = 10;  // 鎖的持有時間（秒）

    private static final long MAX_RETRIES = 5;
    private static final long RETRY_DELAY_BASE = 150;

    public ProductDTO getProductById(Long productId){
        log.info("QUERY product: {}" , productId);

        ProductDTO cached = productCacheService.get(productId);
        if(cached !=null){
            log.info("cache hit: {}" , productId);
            return cached;
        }

        return getProductWithLock(productId);
    }


    private void waitWithBackoff(int retryCount) throws InterruptedException {
        long baseDelay = 50;
        long maxDelay= 2000;

        long delay = Math.min(baseDelay * (1L << retryCount),maxDelay);
        long jitter = ThreadLocalRandom.current().nextLong(0,delay/2);
        Thread.sleep(delay+jitter);
        log.debug("Retry {} after {}ms", retryCount, delay + jitter);

    }

    private ProductDTO getProductWithLock(Long productId){
       String lockKey = "product:get:lock:"+productId;

       RLock lock = lockService.getLock(lockKey);


       for(int retry =0 ; retry<=MAX_RETRIES; retry++){

           try{
               boolean acquired = lock.tryLock(LOCK_WAIT_TIME , LOCK_LEASE_TIME , TimeUnit.SECONDS);
               if(acquired){
                   try{
                       ProductDTO cached = productCacheService.get(productId);

                       if(cached!=null){
                           return cached;
                       }

                       return loadProductFromDB(productId);
                   }finally {
                       if(lock.isHeldByCurrentThread()){
                           lock.unlock();
                       }
                   }
               }else{
                   waitWithBackoff(retry);
                   ProductDTO cached = productCacheService.get(productId);

                   if(cached!=null){
                       return cached;
                   }

                   if(retry==MAX_RETRIES){
                       return loadProductFromDB(productId);
                   }

               }
           }catch (InterruptedException e){
               Thread.currentThread().interrupt();
               throw new RuntimeException("Error");
           }
       }

        throw new RuntimeException("Unexpected error");

    }

    private ProductDTO loadProductFromDB(Long productId){
        log.debug("query from DB");

        Product product = productRepository.findById(productId).orElse(null);

        if(product == null){
            log.warn("Product does not exists: {}",productId);
            productCacheService.setNull(productId);
            return null;
        }

        ProductDTO dto = convertToDto(product);

        productCacheService.set(productId,dto);

        log.info("Query product success from the DB then write into cache success: {}" , productId);
        return dto;

    }

    private ProductDTO convertToDto(Product product){
        return new ProductDTO(
                product.getId() ,
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImageUrl(),
                product.getCategory(),
                product.getIsSeckill()
                );
    }
}
