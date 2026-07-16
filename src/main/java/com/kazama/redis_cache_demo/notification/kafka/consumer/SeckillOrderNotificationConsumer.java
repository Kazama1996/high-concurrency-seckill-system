package com.kazama.redis_cache_demo.notification.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kazama.redis_cache_demo.infra.idempotency.NotificationIdempotencyService;
import com.kazama.redis_cache_demo.seckill.event.SeckillOrderEvent;
import com.kazama.redis_cache_demo.seckill.kafka.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeckillOrderNotificationConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationIdempotencyService idempotencyService;

    // TODO: replace with actual user service lookup when user table is available
    @KafkaListener(
            topics = KafkaTopicConfig.ORDER_NOTIFICATION_TOPIC_NAME,
            groupId = "order-notification",
            containerFactory = "stringKafkaListenerContainerFactory"
    )    public void sendMockEmail(String payload, Acknowledgment acknowledgment) throws JsonProcessingException {
        SeckillOrderEvent orderEvent;

        orderEvent = objectMapper.readValue(payload, SeckillOrderEvent.class);


        if (idempotencyService.isAlreadyProcessed(orderEvent.id())) {
            log.info("Duplicate notification for orderId: {}, skip", orderEvent.id());
            acknowledgment.acknowledge();
            return;
        }

        String email = orderEvent.userId() + "@gmail.com";
        log.info("Sending notification to: {}, orderId: {}, activityId: {}", email, orderEvent.id(), orderEvent.seckillActivityId());
        // if a real send call above throws, this method propagates the exception so the
        // container's error handler retries/dead-letters it instead of acknowledging or
        // marking a notification that was never actually sent

        idempotencyService.markProcessed(orderEvent.id());
        acknowledgment.acknowledge();
    }
}
