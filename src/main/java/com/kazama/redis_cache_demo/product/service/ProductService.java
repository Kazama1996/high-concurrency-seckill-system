package com.kazama.redis_cache_demo.product.service;

import com.kazama.redis_cache_demo.infra.cache.CacheResult;
import com.kazama.redis_cache_demo.infra.bloomfilter.impl.ProductBloomFilterService;
import com.kazama.redis_cache_demo.infra.lock.DistributeLockService;
import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.product.dto.UpdateProductRequest;
import com.kazama.redis_cache_demo.product.entity.Product;
import com.kazama.redis_cache_demo.product.events.ProductDeleteEvent;
import com.kazama.redis_cache_demo.product.events.ProductUpdateEvent;
import com.kazama.redis_cache_demo.product.exception.ProductNotFoundException;
import com.kazama.redis_cache_demo.product.repository.ProductRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.ServiceUnavailableException;
import java.util.List;
import java.util.Optional;
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
    private final ProductBloomFilterService productBloomFilterService;
    private final DistributeLockService lockService;
    private final ApplicationEventPublisher eventPublisher;

    private final CircuitBreaker productDBCircuitBreaker;

    private static final long LOCK_WAIT_TIME = 3;  // 等待鎖的最大時間（秒）
    private static final long LOCK_LEASE_TIME = 10;  // 鎖的持有時間（秒）

    private static final long MAX_RETRIES = 5;
    private static final long RETRY_DELAY_BASE = 150;
    static final long MAX_RETRY_DELAY= 2000;


    public ProductDTO getProductById(Long productId) throws ServiceUnavailableException {
        log.info("QUERY product: {}" , productId);

        if(!productBloomFilterService.mightContain(productId)){
            return null ;
        }

        CacheResult<ProductDTO> productDTOCacheResult = productCacheService.get(productId);

        if(NULL_HIT.equals(productDTOCacheResult.status())){
            log.info("cache hit null val");
            return null;
        }

        if(HIT.equals(productDTOCacheResult.status()) ){
            log.info("cache hit: {}" , productId);
            return productDTOCacheResult.value();
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


       for(int retry =0 ; retry<MAX_RETRIES; retry++){

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
               }
           }catch (InterruptedException e){
               Thread.currentThread().interrupt();
               throw new RuntimeException("Interrupted while waiting for lock, productId: " + productId, e);
           }
       }

        log.error("Failed to acquire lock after {} retries, productId: {}", MAX_RETRIES, productId);
        throw new RuntimeException("Failed to acquire lock after max retries");

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

    @Transactional
    public ProductDTO updateProduct(Long id, UpdateProductRequest request) {
        log.info("UPDATE product: {}", id);

        if (!productBloomFilterService.mightContain(id)) {
            throw new RuntimeException("Product not found: " + id);
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));

        if (request.name() != null) product.setName(request.name());
        if (request.description() != null) product.setDescription(request.description());
        if (request.price() != null) product.setPrice(request.price());
        if (request.imageUrl() != null) product.setImageUrl(request.imageUrl());
        if (request.stock()!=null) product.setStock(request.stock());

        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductUpdateEvent(id));

        return toDTO(saved);
    }

    @Transactional
    public void deleteProductById(Long id){
        log.info("DELETE product: {}" , id);

        if(!productBloomFilterService.mightContain(id)){
            log.info("DELETE product not found (bloom filter rejected): {}", id);
            throw  new ProductNotFoundException(id);
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        productRepository.delete(product);
        log.info("DELETE product from DB success: {}", id);
        eventPublisher.publishEvent(new ProductDeleteEvent(id));

    }

    private ProductDTO toDTO(Product product){
        return new ProductDTO(
                product.getId() ,
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                product.getCategory(),
                product.getIsSeckill());
    }

    @Transactional
    public void markAsSeckill(List<Long> productIds) {


        productRepository.markAsSeckill(productIds);

        productIds.forEach(id->  eventPublisher.publishEvent(new ProductUpdateEvent(id)));

        log.info("Product {} marked as seckill", productIds);
    }
}
