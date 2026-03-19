package com.kazama.redis_cache_demo.seckill.service;

import com.kazama.redis_cache_demo.infra.bloomfilter.impl.ProductBloomFilterService;
import com.kazama.redis_cache_demo.infra.bloomfilter.impl.SeckillActivityBloomFilterService;
import com.kazama.redis_cache_demo.infra.cache.CacheResult;
import com.kazama.redis_cache_demo.infra.exception.ServiceUnavailableException;
import com.kazama.redis_cache_demo.infra.lock.DistributeLockService;
import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.product.entity.Product;
import com.kazama.redis_cache_demo.product.exception.InsufficientStockException;
import com.kazama.redis_cache_demo.product.exception.ProductNotFoundException;
import com.kazama.redis_cache_demo.product.repository.ProductRepository;
import com.kazama.redis_cache_demo.product.service.ProductService;
import com.kazama.redis_cache_demo.seckill.dto.CreateSeckillActivityRequest;
import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.entity.SeckillActivity;
import com.kazama.redis_cache_demo.seckill.enums.Status;
import com.kazama.redis_cache_demo.seckill.event.SeckillActivityCreatedEvent;
import com.kazama.redis_cache_demo.seckill.exception.SeckillActivityNotFoundException;
import com.kazama.redis_cache_demo.seckill.exception.SeckillActivityOverlappingException;
import com.kazama.redis_cache_demo.seckill.repository.SeckillActivityRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.kazama.redis_cache_demo.infra.cache.Status.NULL_HIT;

@Slf4j
@RequiredArgsConstructor
@Service
public class SeckillActivityService {

    private final ProductBloomFilterService productBloomFilterService;

    private final SeckillActivityBloomFilterService seckillActivityBloomFilterService;

    private final SeckillActivityRepository seckillActivityRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final ProductService productService;

    private final ProductRepository productRepository;

    private final SeckillActivityCacheService seckillActivityCacheService;

    private final DistributeLockService lockService;

    @Qualifier("seckillActivityCircuitBreaker")
    private final CircuitBreaker seckillActivityCircuitBreaker;

    private final int MAX_RETRIES =5;

    private static final long LOCK_WAIT_TIME = 3;  // 等待鎖的最大時間（秒）
    private static final long LOCK_LEASE_TIME = 10;  // 鎖的持有時間（秒）

    private static final long RETRY_DELAY_BASE = 150;
    static final long MAX_RETRY_DELAY= 2000;

    private void waitWithBackoff(int retryCount) throws InterruptedException {

        long delay = Math.min(RETRY_DELAY_BASE * (1L << retryCount),MAX_RETRY_DELAY);
        long jitter = ThreadLocalRandom.current().nextLong(0,delay/2);
        Thread.sleep(delay+jitter);
        log.debug("Retry {} after {}ms", retryCount, delay + jitter);

    }

    private void writeActivityAndStockIntoCache(SeckillActivityDTO dto){

        long ttl = ChronoUnit.SECONDS.between(ZonedDateTime.now(), dto.endTime());

        if (ttl <= 0) {
            log.warn("Activity {} already ended, skip cache warming", dto.id());
            setNullByActivityId(dto.id());
            throw new SeckillActivityNotFoundException("Activity already ended: " + dto.id());
        }

        seckillActivityCacheService.setActivity(dto.id() , dto, ttl);
        seckillActivityCacheService.setStock(dto.id() , dto.remainingStock() , ttl);
    }

    private void setNullByActivityId(Long activityId){
        seckillActivityCacheService.setNullActivity(activityId);
        seckillActivityCacheService.setNullStock(activityId);
    }


    private SeckillActivityDTO loadFormDB(Long activityId)  {
        log.debug("query activity from DB");


        if(!seckillActivityCircuitBreaker.tryAcquirePermission()){
            log.warn("Circuit breaker OPEN, skip DB query for seckill activity: {}" , activityId );
            throw new ServiceUnavailableException("DB circuit breaker is open");
        }

        long start = System.currentTimeMillis();

        try{
            SeckillActivityDTO seckillActivityById = seckillActivityRepository.findSeckillActivityById(activityId)
                    .orElseGet(() -> {
                        setNullByActivityId(activityId);
                        throw new SeckillActivityNotFoundException("Seckill activity not found" + activityId);
                    });
            long duration = System.currentTimeMillis() - start;
            seckillActivityCircuitBreaker.onSuccess(duration , TimeUnit.MILLISECONDS);


            writeActivityAndStockIntoCache(seckillActivityById);
            log.info("Query seckill activity success from the DB then write into cache success: {}" , activityId);
            return seckillActivityById;


        }catch (Exception e){
            long duration = System.currentTimeMillis()-start;
            seckillActivityCircuitBreaker.onError(duration , TimeUnit.MILLISECONDS , e);
            throw  e;
        }

    }

    private SeckillActivityDTO getSeckillActivityWithLock(Long activityId) {
        String lockKey ="seckill:activity:rewarm:"+activityId;

        RLock lock = lockService.getLock(lockKey);


        for(int retry=0 ; retry< MAX_RETRIES; retry++){
            try{
                boolean acquired = lock.tryLock(LOCK_WAIT_TIME , LOCK_LEASE_TIME , TimeUnit.SECONDS);
                if(acquired){
                    try{
                        CacheResult<SeckillActivityDTO> cacheResult = seckillActivityCacheService.getActivity(activityId);
                        if(NULL_HIT.equals(cacheResult.status())){
                            throw new SeckillActivityNotFoundException("Seckill activity not found" + activityId);
                        }else if (com.kazama.redis_cache_demo.infra.cache.Status.HIT.equals(cacheResult.status())) {
                            return cacheResult.value();
                        }

                        return loadFormDB(activityId);
                    }finally {
                        if(lock.isHeldByCurrentThread()){
                            lock.unlock();
                        }
                    }

                }else{
                    waitWithBackoff(retry);
                    CacheResult<SeckillActivityDTO> cacheResult = seckillActivityCacheService.getActivity(activityId);
                    if(com.kazama.redis_cache_demo.infra.cache.Status.HIT.equals(cacheResult.status())){
                        return cacheResult.value();
                    }
                    if (NULL_HIT.equals(cacheResult.status())) {
                        throw new SeckillActivityNotFoundException("Seckill activity not found: " + activityId);
                    }
                }
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for lock, activityId: " + activityId, e);

            }
        }

        log.error("Failed to acquire lock after {} retries, productId: {}", MAX_RETRIES, activityId);
        throw new RuntimeException("Failed to acquire lock after max retries");

    }



    public SeckillActivityDTO rewarming(Long activityId)  {


        if(!seckillActivityBloomFilterService.mightContain(activityId)){
            log.info("Seckill activity does not exists in Seckill activity bloomfilter");
            setNullByActivityId(activityId);
            throw new SeckillActivityNotFoundException("Seckill activity does not found"+activityId);
        }
        return getSeckillActivityWithLock(activityId);


    }




    private List<SeckillActivity> buildEntityList(List<CreateSeckillActivityRequest> requests,Map<Long, Product> productMap){
        return requests.stream().map(request-> {

            Product product = productMap.get(request.productId());

            return  SeckillActivity.builder()
                    .productId(request.productId())
                    .originalPrice(product.getPrice())
                    .seckillPrice(request.seckillPrice())
                    .totalStock(request.totalStock())
                    .remainingStock(request.totalStock())  // 跟 totalStock 相同
                    .maxQuantityPerOrder(request.maxQuantityPerOrder())
                    .startTime(request.dateRange().startTime())
                    .endTime(request.dateRange().endTime())
                    .status(Status.PENDING)
                    .build();
        }).toList();
    }


    private void validateBloomFilter(List<CreateSeckillActivityRequest> requests){
        for(CreateSeckillActivityRequest request : requests){
            if(!productBloomFilterService.mightContain(request.productId())){
                log.info("product does not exists in product bloomfilter");
                throw new ProductNotFoundException(request.productId());
            }
        }
    }

    private void validateStock(List<CreateSeckillActivityRequest> requests , Map<Long, Product> productMap ){

        for (CreateSeckillActivityRequest request : requests) {
            Product product = productMap.get(request.productId());
            if (product == null) {
                throw new ProductNotFoundException(request.productId());
            }
            if (product.getStock() < request.totalStock()) {
                throw new InsufficientStockException(
                        String.format("Product %s stock %d is less than requested %d",
                                request.productId(), product.getStock(), request.totalStock())
                );
            }
        }
    }

    private void validateInternalOverlap(List<CreateSeckillActivityRequest> requests ){
        Map<Long, List<CreateSeckillActivityRequest>> groupByProduct = requests.stream()
                .collect(Collectors.groupingBy(CreateSeckillActivityRequest::productId));

        groupByProduct.forEach((productId, activities) -> {
            activities.stream()
                    .sorted(Comparator.comparing(r -> r.dateRange().startTime()))
                    .reduce((prev, curr) -> {
                        if (prev.dateRange().endTime().isAfter(curr.dateRange().startTime())) {
                            throw new SeckillActivityOverlappingException(
                                    String.format("Product %s has overlapping activities in request", productId)
                            );
                        }
                        return curr;
                    });
        });
    }

    private void validateExternalOverlap(List<CreateSeckillActivityRequest> requests , List<Long> productIds){
        ZonedDateTime minStart = requests.stream()
                .map(r -> r.dateRange().startTime())
                .min(Comparator.naturalOrder())
                .orElseThrow();

        ZonedDateTime maxEnd = requests.stream()
                .map(r -> r.dateRange().endTime())
                .max(Comparator.naturalOrder())
                .orElseThrow();

        List<SeckillActivity> overlappingActivity = seckillActivityRepository.findOverlappingActivities(productIds, minStart, maxEnd);

        Map<Long, List<SeckillActivity>> existingByProduct = overlappingActivity.stream()
                .collect(Collectors.groupingBy(SeckillActivity::getProductId));

        for (CreateSeckillActivityRequest request : requests) {
            List<SeckillActivity> existing = existingByProduct
                    .getOrDefault(request.productId(), List.of());

            boolean hasOverlap = existing.stream().anyMatch(e ->
                    e.getStartTime().isBefore(request.dateRange().endTime()) &&
                            e.getEndTime().isAfter(request.dateRange().startTime())
            );

            if (hasOverlap) {
                throw new SeckillActivityOverlappingException(
                        String.format("Product %s has overlapping activity", request.productId())
                );
            }
        }
    }



    @Transactional
    public List<SeckillActivityDTO> createActivities(List<CreateSeckillActivityRequest> requests){

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Request list cannot be null or empty");
        }

        log.info("CREATE seckill activities for {} products", requests.size());

        List<Long> productIds = requests.stream()
                .map(CreateSeckillActivityRequest::productId)
                .distinct()
                .toList();

        Map<Long, Product> productMap = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p));


        // Step 1 Bloom filter
        validateBloomFilter(requests);

        // Step 2 validate overlap condition in request itself
        validateInternalOverlap(requests);

        // Step 3  check Product is not out of stock
        validateStock(requests,productMap);

        // Step 4. validate overlap condition in DB
        validateExternalOverlap(requests, productIds);

        // Step 5. buildEntityList
        List<SeckillActivity> seckillActivities = buildEntityList(requests, productMap);

        // Step 6 saveAll
        List<SeckillActivity> saved = seckillActivityRepository.saveAll(seckillActivities);

        // Step 7 mark these product as is_Seckill = true
        productService.markAsSeckill(productIds);

        // step 8 publish SeckillActivityCreatedEvent
        saved.forEach(activity ->
                eventPublisher.publishEvent(new SeckillActivityCreatedEvent(
                        activity.getId(),
                        activity.getProductId(),
                        activity.getStartTime(),
                        activity.getEndTime()
                ))
        );

        log.info("CREATE seckill activities success, count: {}", saved.size());

        // Step 9 return
        return saved.stream().map(this::toDTO).toList();

    }




    SeckillActivityDTO toDTO(SeckillActivity activity){

        return new SeckillActivityDTO(
                activity.getId(),
                activity.getProductId(),
                activity.getOriginalPrice(),
                activity.getSeckillPrice(),
                activity.getTotalStock(),
                activity.getRemainingStock(),
                activity.getMaxQuantityPerOrder(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getStatus());
    }
}
