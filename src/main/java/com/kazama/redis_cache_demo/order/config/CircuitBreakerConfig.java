package com.kazama.redis_cache_demo.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CircuitBreakerConfig {


    @Bean("orderDBCircuitBreaker")
    public CircuitBreaker orderDBCircuitBreaker(CircuitBreakerRegistry registry){
        return registry.circuitBreaker("orderDB");
    }

}
