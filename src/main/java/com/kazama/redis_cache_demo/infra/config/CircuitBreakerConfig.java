package com.kazama.redis_cache_demo.infra.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;


@Configuration
public class CircuitBreakerConfig {


    @Bean("productDBCircuitBreaker")
    public CircuitBreaker productDBCircuitBreaker(CircuitBreakerRegistry registry){
        return registry.circuitBreaker("productDB");
    }

    @Bean("redisCircuitBreaker")
    public CircuitBreaker redisCircuitBreaker(CircuitBreakerRegistry registry){
        return registry.circuitBreaker("redis");
    }

    @Bean("seckillActivityCircuitBreaker")
    public CircuitBreaker seckillActivityCircuitBreaker(CircuitBreakerRegistry registry){
        return registry.circuitBreaker("seckillActivityDB");
    }


}
