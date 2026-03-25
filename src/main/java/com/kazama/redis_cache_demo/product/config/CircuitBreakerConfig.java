package com.kazama.redis_cache_demo.product.config;


import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration("ProductCircuitBreakerConfig")
public class CircuitBreakerConfig {

    @Bean("productDBCircuitBreaker")
    public CircuitBreaker productDBCircuitBreaker(CircuitBreakerRegistry registry){
        return registry.circuitBreaker("productDB");
    }
}
