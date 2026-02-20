package com.kazama.redis_cache_demo.infra.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;


@Configuration
public class CircuitBreakerConfig {


    @Bean
    public CircuitBreaker productDBCircuitBreaker(CircuitBreakerRegistry registry){
        return registry.circuitBreaker("productDB");
    }


}
