package com.kazama.redis_cache_demo.seckill.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_NOTIFICATION_TOPIC_NAME = "seckill.order.notification";

    public static final int NUM_OF_CREATE_ORDER_TOPIC  =3 ;

    @Bean
    public NewTopic orderNotification(){
        return new NewTopic(ORDER_NOTIFICATION_TOPIC_NAME , 3 , (short) 1);
    }
}
