package com.kazama.redis_cache_demo.infra.circuitbreaker.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;


@Configuration("infraCircuitBreakerConfig")
public class CircuitBreakerConfig {

    @Bean("redisCircuitBreaker")
    public CircuitBreaker redisCircuitBreaker(CircuitBreakerRegistry registry){
        return registry.circuitBreaker("redis");
    }
}
