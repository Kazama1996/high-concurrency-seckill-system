package com.kazama.redis_cache_demo.seckill.kafka.producer;

import com.kazama.redis_cache_demo.seckill.kafka.config.KafkaTopicConfig;
import com.kazama.redis_cache_demo.seckill.event.SeckillOrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class SeckillOrderProducer {

    private final KafkaTemplate<String , SeckillOrderEvent> kafkaTemplate;

    public void publishMessage(SeckillOrderEvent seckillOrderEvent){
        kafkaTemplate.send(KafkaTopicConfig.CREATE_ORDER_TOPIC_NAME, seckillOrderEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send message: {}", ex.getMessage());
                    } else {
                        log.info("Message sent successfully to partition: {}, offset: {}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });    }

}
