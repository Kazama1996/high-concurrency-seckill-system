package com.kazama.redis_cache_demo.product.events;

import com.kazama.redis_cache_demo.product.service.ProductCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCacheEventListener {

    private final ProductCacheService productCacheService;


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductUpdated(ProductUpdateEvent event){
        log.info("DB committed, deleting cache for productId: {}", event.productId());
        productCacheService.delete(event.productId());
        log.info("UPDATE product success: {}" , event.productId());

    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductDeleted(ProductDeleteEvent event){
        log.info("DB committed, deleting cache for productId: {}" , event.productId());
        productCacheService.delete(event.productId());
        log.info("DELETE product success: {}" , event.productId());

    }
}
