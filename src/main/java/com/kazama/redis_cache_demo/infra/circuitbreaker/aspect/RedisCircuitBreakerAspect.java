package com.kazama.redis_cache_demo.infra.circuitbreaker.aspect;

import com.kazama.redis_cache_demo.infra.circuitbreaker.annotation.RedisCircuitBreaker;
import com.kazama.redis_cache_demo.infra.exception.RedisUnavailableException;
import com.kazama.redis_cache_demo.infra.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(1)
public class RedisCircuitBreakerAspect {



    @Qualifier("redisCircuitBreaker")
    private final CircuitBreaker redisCircuitBreaker;



    @Around("@annotation(circuitBreaker)")
    public Object around(ProceedingJoinPoint joinPoint , RedisCircuitBreaker circuitBreaker) throws Throwable {


        if(!redisCircuitBreaker.tryAcquirePermission()){
            log.warn("Redis Circuit breaker OPEN, redis is unavailable");
            throw new RedisUnavailableException("Redis circuit breaker is open");
        }

        long start = System.currentTimeMillis();

        try{
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            redisCircuitBreaker.onSuccess(duration, TimeUnit.MILLISECONDS);
            return result;
        }catch(Exception e){

            if (e instanceof RedisException || e instanceof TimeoutException) {
                long duration = System.currentTimeMillis() - start;
                redisCircuitBreaker.onError(duration, TimeUnit.MILLISECONDS, e);
            }
            throw e;
        }



    }
}
