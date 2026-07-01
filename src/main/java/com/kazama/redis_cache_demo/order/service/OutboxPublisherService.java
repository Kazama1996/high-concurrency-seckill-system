package com.kazama.redis_cache_demo.order.service;

import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxStatusUpdateService statusUpdateService;


    public void publish(OrderCreatedOutbox outbox) {
        kafkaTemplate.send(outbox.getTopicName(), String.valueOf(outbox.getOrderId()), outbox.getPayload())
                .whenComplete((result, ex) -> {
                    try {
                        if (ex == null) {
                            statusUpdateService.markSent(outbox.getId());
                        } else {
                            log.error("Failed to send outbox message, id: {}", outbox.getId(), ex);
                            statusUpdateService.markFailed(outbox.getId(), OutboxStatusUpdateService.MAX_RETRY_ATTEMPTS);
                        }
                    } catch (Exception dbEx) {
                        log.error("Failed to update outbox status, id: {}", outbox.getId(), dbEx);
                    }
                });
    }
}
