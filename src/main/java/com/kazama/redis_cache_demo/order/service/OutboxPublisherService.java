package com.kazama.redis_cache_demo.order.service;

import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import com.kazama.redis_cache_demo.order.repository.OrderCreatedOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OrderCreatedOutboxRepository orderCreatedOutboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final long SEND_TIMEOUT_SECONDS = 5;

    @Transactional
    public void publish(OrderCreatedOutbox outbox) {
        try {
            kafkaTemplate.send(outbox.getTopicName(), outbox.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outbox.setStatus(OutboxStatus.SENT);
        } catch (Exception e) {
            log.error("Failed to send outbox message, id: {}", outbox.getId(), e);
            outbox.setStatus(OutboxStatus.FAILED);
        }
        orderCreatedOutboxRepository.save(outbox);
    }
}
