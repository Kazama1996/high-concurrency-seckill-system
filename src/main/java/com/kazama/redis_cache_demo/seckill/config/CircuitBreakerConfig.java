package com.kazama.redis_cache_demo.seckill.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration("SeckillActivityCircuitBreakerConfig")
public class CircuitBreakerConfig {

    @Bean("seckillActivityCircuitBreaker")
    public CircuitBreaker seckillActivityCircuitBreaker(CircuitBreakerRegistry registry){
        return registry.circuitBreaker("seckillActivityDB");
    }

}
