package com.kazama.redis_cache_demo.order.service;

import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.repository.OrderCreatedOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxStatusUpdateService {

    public static final int MAX_RETRY_ATTEMPTS = 5;

    private final OrderCreatedOutboxRepository orderCreatedOutboxRepository;

    @Transactional
    public void markSent(Long id) {
        orderCreatedOutboxRepository.bulkUpdateStatus(List.of(id), OutboxStatus.SENT);
    }

    @Transactional
    public void markFailed(Long id, int maxRetry) {
        orderCreatedOutboxRepository.bulkMarkFailed(List.of(id), maxRetry);
    }

    @Transactional
    public void markFailedBatch(List<Long> ids, int maxRetry) {
        orderCreatedOutboxRepository.bulkMarkFailed(ids, maxRetry);
    }
}
