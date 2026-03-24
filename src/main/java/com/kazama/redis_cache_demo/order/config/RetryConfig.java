package com.kazama.redis_cache_demo.order.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RetryConfig {

    @Bean("orderCreationRetry")
    public Retry orderCreationRetry(RetryRegistry registry){
        return registry.retry("orderCreation");
    }
}
