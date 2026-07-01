package com.kazama.redis_cache_demo.order.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import com.kazama.redis_cache_demo.order.entity.Orders;
import com.kazama.redis_cache_demo.order.event.OrderCreatedEvent;
import com.kazama.redis_cache_demo.order.repository.OrderCreatedOutboxRepository;
import com.kazama.redis_cache_demo.order.repository.OrderRepository;
import com.kazama.redis_cache_demo.seckill.kafka.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    private final OrderCreatedOutboxRepository orderCreatedOutboxRepository;

    private final ObjectMapper objectMapper;


    @Transactional
    public Orders createOrder(Orders entity){
      log.debug("create order :{}" ,entity);

        OrderCreatedEvent event = new OrderCreatedEvent(
                entity.getId(),
                entity.getSeckillActivityId(),
                entity.getProductId(),
                entity.getUserId(),
                entity.getQuantity(),
                entity.getOriginalPrice(),
                entity.getSeckillPrice()
        );
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event, skip. activityId: {}", entity.getSeckillActivityId(), e);
            throw new RuntimeException("fail to parse order created outbox payload");
        }
        OrderCreatedOutbox outbox = OrderCreatedOutbox
                .builder()
                .orderId(entity.getId())
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .topicName(KafkaTopicConfig.ORDER_NOTIFICATION_TOPIC_NAME)
                .build();


        Orders res = orderRepository.save(entity);
        orderCreatedOutboxRepository.save(outbox);

        return res;

    }
}
