package com.kazama.redis_cache_demo.notification.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kazama.redis_cache_demo.seckill.event.SeckillOrderEvent;
import com.kazama.redis_cache_demo.seckill.kafka.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeckillOrderNotificationConsumer {

    private final ObjectMapper objectMapper;

    // TODO: replace with actual user service lookup when user table is available
    @KafkaListener(
            topics = KafkaTopicConfig.ORDER_NOTIFICATION_TOPIC_NAME,
            groupId = "order-notification",
            containerFactory = "stringKafkaListenerContainerFactory"
    )    public void sendMockEmail(String payload) {
        SeckillOrderEvent orderEvent;
        try {
            orderEvent = objectMapper.readValue(payload, SeckillOrderEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize notification payload, skip. payload: {}", payload, e);
            return;
        }
        String email = orderEvent.userId() + "@gmail.com";
        log.info("Sending notification to: {}, activityId: {}", email, orderEvent.activityId());
    }
}
