package com.kazama.redis_cache_demo.order.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kazama.redis_cache_demo.infra.outbox.entity.Outbox;
import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.infra.outbox.repository.OutboxRepository;
import com.kazama.redis_cache_demo.order.entity.Orders;
import com.kazama.redis_cache_demo.order.enums.OrderStatus;
import com.kazama.redis_cache_demo.order.repository.OrderRepository;
import com.kazama.redis_cache_demo.seckill.event.SeckillOrderEvent;
import com.kazama.redis_cache_demo.seckill.kafka.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeckillOrderConsumer {


    private final OrderRepository orderRepository;

    private final OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper;


    @KafkaListener(topics = KafkaTopicConfig.CREATE_ORDER_TOPIC_NAME , groupId = "order-group" , concurrency = "" + KafkaTopicConfig.NUM_OF_CREATE_ORDER_TOPIC)
    @Transactional
    public void consumer(SeckillOrderEvent event)  {
        Orders entity = Orders
                .builder()
                .productId(event.productId())
                .userId(event.userId())
                .seckillActivityId(event.activityId())
                .originalPrice(event.originalPrice())
                .seckillPrice(event.seckillPrice())
                .quantity(event.quantity())
                .orderStatus(OrderStatus.UNPAID)
                .build();

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event, skip. activityId: {}", event.activityId(), e);
            return;
        }
        Outbox outbox = Outbox
                .builder()
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .topicName(KafkaTopicConfig.ORDER_NOTIFICATION_TOPIC_NAME)
                .build();

        try {
            orderRepository.save(entity);
            outboxRepository.save(outbox);
            log.debug("create order:  productId:{} , activityId:{} , userId:{} , quantity:{}" , event.productId() , event.activityId(),event.userId() , event.quantity());
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate order detected, skip. activityId: {}, userId: {}",
                    event.activityId(), event.userId());
        }



    }
}
