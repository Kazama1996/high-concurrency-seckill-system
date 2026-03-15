package com.kazama.redis_cache_demo.seckill.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    public static final String CREATE_ORDER_TOPIC_NAME = "seckill.order.created";
    public static final String ORDER_NOTIFICATION_TOPIC_NAME = "seckill.order.notification";

    @Bean
    public NewTopic createOrderTopic(){
        return new NewTopic(CREATE_ORDER_TOPIC_NAME , 3, (short) 1);
    }

    @Bean
    public NewTopic orderNotification(){
        return new NewTopic(ORDER_NOTIFICATION_TOPIC_NAME , 3 , (short) 1);
    }
}
