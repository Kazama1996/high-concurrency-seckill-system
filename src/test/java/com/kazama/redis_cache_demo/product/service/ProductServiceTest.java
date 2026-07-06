package com.kazama.redis_cache_demo.product.service;

import com.kazama.redis_cache_demo.infra.bloomfilter.impl.ProductBloomFilterService;
import com.kazama.redis_cache_demo.infra.cache.CacheResult;
import com.kazama.redis_cache_demo.infra.exception.ServiceUnavailableException;
import com.kazama.redis_cache_demo.infra.lock.DistributeLockService;
import com.kazama.redis_cache_demo.infra.util.RandomGenerator;
import com.kazama.redis_cache_demo.infra.util.Sleeper;
import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.product.enums.ProductCategory;
import com.kazama.redis_cache_demo.product.repository.ProductRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductCacheService productCacheService;
    @Mock private ProductBloomFilterService productBloomFilterService;
    @Mock private DistributeLockService lockService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CircuitBreaker productDBCircuitBreaker;
    @Mock private RLock rLock;
    @Mock private Sleeper sleeper;
    @Mock private RandomGenerator randomGenerator;
    ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);

    @InjectMocks private ProductService productService;

    private static final Long PRODUCT_ID = 1L;

    private ProductDTO sampleProduct() {
        return new ProductDTO(
                PRODUCT_ID, "Test Phone", "A smartphone",
                BigDecimal.valueOf(999), 100,
                "http://img.example.com/phone.jpg",
                ProductCategory.ELECTRONICS, false
        );
    }

    // ------------------------------------------------------------------ bloom filter

    @Test
    void getProductById_bloomFilterRejects_returnsNull() {
        when(productBloomFilterService.mightContain(PRODUCT_ID)).thenReturn(false);

        ProductDTO result = productService.getProductById(PRODUCT_ID);

        assertNull(result);
        verifyNoInteractions(productCacheService, lockService, productRepository);
    }

    // ------------------------------------------------------------------ cache hits

    @Test
    void getProductById_cacheNullHit_returnsNull() {
        when(productBloomFilterService.mightContain(PRODUCT_ID)).thenReturn(true);
        when(productCacheService.get(PRODUCT_ID)).thenReturn(CacheResult.nullHit());

        ProductDTO result = productService.getProductById(PRODUCT_ID);

        assertNull(result);
        verifyNoInteractions(lockService, productRepository);
    }

    @Test
    void getProductById_cacheHit_returnsCachedProduct() {
        ProductDTO dto = sampleProduct();
        when(productBloomFilterService.mightContain(PRODUCT_ID)).thenReturn(true);
        when(productCacheService.get(PRODUCT_ID)).thenReturn(CacheResult.hit(dto));

        ProductDTO result = productService.getProductById(PRODUCT_ID);

        assertSame(dto, result);
        verifyNoInteractions(lockService, productRepository);
    }

    // ------------------------------------------------------------------ cache miss → DB

    @Test
    void getProductById_cacheMiss_dbHit_returnsProductAndPopulatesCache() throws InterruptedException {
        ProductDTO dto = sampleProduct();
        when(productBloomFilterService.mightContain(PRODUCT_ID)).thenReturn(true);
        // First call in getProductById → miss; second call inside the lock → miss
        when(productCacheService.get(PRODUCT_ID))
                .thenReturn(CacheResult.miss())
                .thenReturn(CacheResult.miss());
        when(lockService.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(productDBCircuitBreaker.tryAcquirePermission()).thenReturn(true);
        when(productRepository.findProductDTOById(PRODUCT_ID)).thenReturn(Optional.of(dto));

        ProductDTO result = productService.getProductById(PRODUCT_ID);

        assertSame(dto, result);
        verify(productCacheService).set(eq(PRODUCT_ID), eq(dto));
        verify(rLock).unlock();
    }

    @Test
    void getProductById_cacheMiss_dbMiss_returnsNullAndCachesNullSentinel() throws InterruptedException {
        when(productBloomFilterService.mightContain(PRODUCT_ID)).thenReturn(true);
        when(productCacheService.get(PRODUCT_ID))
                .thenReturn(CacheResult.miss())
                .thenReturn(CacheResult.miss());
        when(lockService.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(productDBCircuitBreaker.tryAcquirePermission()).thenReturn(true);
        when(productRepository.findProductDTOById(PRODUCT_ID)).thenReturn(Optional.empty());

        ProductDTO result = productService.getProductById(PRODUCT_ID);

        assertNull(result);
        verify(productCacheService).setNull(PRODUCT_ID);
        verify(rLock).unlock();
    }

    @Test
    void getProductById_cacheMiss_lockAcquired_circuitBreakerOpen_throwsServiceUnavailableException()
            throws InterruptedException {
        when(productBloomFilterService.mightContain(PRODUCT_ID)).thenReturn(true);
        when(productCacheService.get(PRODUCT_ID))
                .thenReturn(CacheResult.miss())
                .thenReturn(CacheResult.miss());
        when(lockService.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(productDBCircuitBreaker.tryAcquirePermission()).thenReturn(false);

        assertThrows(ServiceUnavailableException.class,
                () -> productService.getProductById(PRODUCT_ID));

        verify(productRepository, never()).findProductDTOById(any());
        verify(rLock).unlock();
    }

    @Test
    void getProductById_cacheMiss_lockAcquired_cacheWarmedByOtherThread_returnsCachedProduct()
            throws InterruptedException {
        // Simulates the double-check inside the lock finding a cache hit (another thread warmed it)
        ProductDTO dto = sampleProduct();
        when(productBloomFilterService.mightContain(PRODUCT_ID)).thenReturn(true);
        when(productCacheService.get(PRODUCT_ID))
                .thenReturn(CacheResult.miss())      // first check before acquiring lock
                .thenReturn(CacheResult.hit(dto));   // second check after acquiring lock
        when(lockService.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        ProductDTO result = productService.getProductById(PRODUCT_ID);

        assertSame(dto, result);
        verifyNoInteractions(productRepository);
        verify(rLock).unlock();
    }

    @Test
    void getProductById_cacheMiss_lockNotAcquired_retryThenCacheHit() throws InterruptedException {

        ProductDTO dto =sampleProduct();
        when(productBloomFilterService.mightContain(PRODUCT_ID)).thenReturn(true);
        when(productCacheService.get(PRODUCT_ID))
                .thenReturn(CacheResult.miss())   // getProductById 外层第一次查
                .thenReturn(CacheResult.hit(dto)); // retry=0 进入 else 分支,waitWithBackoff 后查的那
        when(lockService.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong() , anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(randomGenerator.nextLong(anyLong())).thenReturn(0L);


        ProductDTO result = productService.getProductById(PRODUCT_ID);

        assertSame(dto , result);

        verifyNoInteractions(productRepository);
        verify(rLock, times(1)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        verify(sleeper).sleep(150L);

    }


    @Test
    void getProductById_cacheMiss_lockNotAcquired_reachMaxRetries() throws InterruptedException {

        when(productBloomFilterService.mightContain(PRODUCT_ID)).thenReturn(true);
        when(productCacheService.get(PRODUCT_ID)).thenReturn(CacheResult.miss());
        when(lockService.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(),anyLong(),any(TimeUnit.class))).thenReturn(false);
        when(randomGenerator.nextLong(anyLong())).thenReturn(0L);


        assertThrows(RuntimeException.class ,()-> productService.getProductById(PRODUCT_ID));

        verify(rLock, times(5)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        verify(sleeper, times(5)).sleep(delayCaptor.capture());
        verify(rLock, never()).unlock();

        List<Long> capturedDelays = delayCaptor.getAllValues();
        assertEquals(List.of(150L, 300L, 600L, 1200L, 2000L), capturedDelays);

    }
}
