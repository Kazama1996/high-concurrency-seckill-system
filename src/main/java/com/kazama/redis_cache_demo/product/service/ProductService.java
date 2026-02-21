package com.kazama.redis_cache_demo.product.service;

import com.kazama.redis_cache_demo.infra.cache.BloomFilterService;
import com.kazama.redis_cache_demo.infra.cache.CacheResult;
import com.kazama.redis_cache_demo.infra.lock.DistributeLockService;
import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.product.repository.ProductRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import javax.naming.ServiceUnavailableException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.kazama.redis_cache_demo.infra.cache.Status.HIT;
import static com.kazama.redis_cache_demo.infra.cache.Status.NULL_HIT;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCacheService productCacheService;
    private final BloomFilterService bloomFilterService;
    private final DistributeLockService lockService;

    private final CircuitBreaker productDBCircuitBreaker;

    private static final long LOCK_WAIT_TIME = 3;  // 等待鎖的最大時間（秒）
    private static final long LOCK_LEASE_TIME = 10;  // 鎖的持有時間（秒）

    private static final long MAX_RETRIES = 5;
    private static final long RETRY_DELAY_BASE = 150;
    static final long MAX_RETRY_DELAY= 2000;


    public ProductDTO getProductById(Long productId) throws ServiceUnavailableException {
        log.info("QUERY product: {}" , productId);

        CacheResult<ProductDTO> productDTOCacheResult = productCacheService.get(productId);

        if(NULL_HIT.equals(productDTOCacheResult.status())){
            log.info("cache hit null val");
            return null;
        }

        if(HIT.equals(productDTOCacheResult.status()) ){
            log.info("cache hit: {}" , productId);
            return productDTOCacheResult.value();
        }

        if(!bloomFilterService.mightContainProduct(productId)){
            return null ;
        }

        return getProductWithLock(productId);
    }


    private void waitWithBackoff(int retryCount) throws InterruptedException {

        long delay = Math.min(RETRY_DELAY_BASE * (1L << retryCount),MAX_RETRY_DELAY);
        long jitter = ThreadLocalRandom.current().nextLong(0,delay/2);
        Thread.sleep(delay+jitter);
        log.debug("Retry {} after {}ms", retryCount, delay + jitter);

    }

    private ProductDTO getProductWithLock(Long productId) throws ServiceUnavailableException {
       String lockKey = "product:get:lock:"+productId;

       RLock lock = lockService.getLock(lockKey);


       for(int retry =0 ; retry<=MAX_RETRIES; retry++){

           try{
               boolean acquired = lock.tryLock(LOCK_WAIT_TIME , LOCK_LEASE_TIME , TimeUnit.SECONDS);
               if(acquired){
                   try{
                       CacheResult<ProductDTO> productDTOCacheResult = productCacheService.get(productId);

                       if(NULL_HIT.equals(productDTOCacheResult.status())){
                           log.info("cache hit null val");
                           return null;
                       }

                       if(HIT.equals(productDTOCacheResult.status()) ){
                           log.info("cache hit: {}" , productId);
                           return productDTOCacheResult.value();
                       }

                       return loadProductFromDB(productId);
                   }finally {
                       if(lock.isHeldByCurrentThread()){
                           lock.unlock();
                       }
                   }
               }else{
                   waitWithBackoff(retry);
                   CacheResult<ProductDTO> productDTOCacheResult = productCacheService.get(productId);

                   if(NULL_HIT.equals(productDTOCacheResult.status())){
                       log.info("cache hit null val");
                       return null;
                   }

                   if(HIT.equals(productDTOCacheResult.status()) ){
                       log.info("cache hit: {}" , productId);
                       return productDTOCacheResult.value();
                   }

                   if(retry==MAX_RETRIES){
                       throw new RuntimeException("Failed to acquire lock after max retries");
                   }

               }
           }catch (InterruptedException e){
               Thread.currentThread().interrupt();
               throw new RuntimeException("Error");
           }
       }

        // unreachable code compiler needed
        throw new RuntimeException("Unexpected error");

    }

    private ProductDTO loadProductFromDB(Long productId) throws ServiceUnavailableException {
        log.debug("query from DB");

        if(!productDBCircuitBreaker.tryAcquirePermission()){
            log.warn("Circuit breaker OPEN, skip DB query for product: {}", productId);
            throw new ServiceUnavailableException("DB circuit breaker is open");
        }
        long start = System.currentTimeMillis();

        try{
            ProductDTO productDTO = productRepository.findProductDTOById(productId).orElse(null);
            long duration = System.currentTimeMillis() - start;
            productDBCircuitBreaker.onSuccess(duration , TimeUnit.MILLISECONDS);

            if(productDTO == null){
                log.warn("Product does not exists: {}",productId);
                productCacheService.setNull(productId);
                return null;
            }

            productCacheService.set(productId,productDTO);
            log.info("Query product success from the DB then write into cache success: {}" , productId);
            return productDTO;
        }catch (Exception e){
            long duration = System.currentTimeMillis()-start;
            productDBCircuitBreaker.onError(duration , TimeUnit.MILLISECONDS , e);
            throw e;
        }
    }
}
