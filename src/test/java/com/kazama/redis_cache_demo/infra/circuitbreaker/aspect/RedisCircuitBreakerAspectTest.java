package com.kazama.redis_cache_demo.infra.circuitbreaker.aspect;

import com.kazama.redis_cache_demo.infra.circuitbreaker.annotation.RedisCircuitBreaker;
import com.kazama.redis_cache_demo.infra.exception.RedisUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.client.RedisException;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCircuitBreakerAspectTest {

    @Mock
    private CircuitBreaker circuitBreaker;

    private RedisCircuitBreakerAspect aspect;
    private TestTarget target;
    private TestTarget proxy;

    @BeforeEach
    void setUp() {
        aspect = new RedisCircuitBreakerAspect(circuitBreaker);

        target = new TestTarget();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    @Test
    void permitted_methodSucceeds_recordsSuccess() {
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);

        String result = proxy.execute();

        assertEquals("executed", result);
        verify(circuitBreaker).onSuccess(anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(circuitBreaker, never()).onError(anyLong(), any(), any());
    }

    @Test
    void breakerOpen_throwsRedisUnavailableException_methodNeverInvoked() {
        when(circuitBreaker.tryAcquirePermission()).thenReturn(false);

        assertThrows(RedisUnavailableException.class, proxy::execute);
        assertFalse(target.invoked);
        verify(circuitBreaker, never()).onSuccess(anyLong(), any());
        verify(circuitBreaker, never()).onError(anyLong(), any(), any());
    }

    @Test
    void redissonFailure_isRecordedAsError() {
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);
        target.exceptionToThrow = new RedisException("redisson down");

        assertThrows(RedisException.class, proxy::execute);
        verify(circuitBreaker).onError(anyLong(), eq(TimeUnit.MILLISECONDS), eq(target.exceptionToThrow));
    }

    // Regression test: this call path goes through Spring Data Redis (Lettuce),
    // not Redisson, so the aspect must classify DataAccessException as a failure too.
    @Test
    void springDataRedisFailure_isRecordedAsError() {
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);
        target.exceptionToThrow = new RedisConnectionFailureException("lettuce connection refused");

        assertThrows(RedisConnectionFailureException.class, proxy::execute);
        verify(circuitBreaker).onError(anyLong(), eq(TimeUnit.MILLISECONDS), eq(target.exceptionToThrow));
    }

    @Test
    void unrelatedException_isNotRecordedAsError() {
        when(circuitBreaker.tryAcquirePermission()).thenReturn(true);
        target.exceptionToThrow = new IllegalStateException("business logic failure");

        assertThrows(IllegalStateException.class, proxy::execute);
        verify(circuitBreaker, never()).onError(anyLong(), any(), any());
    }

    // Minimal test target — annotated so the aspect can intercept it
    static class TestTarget {
        boolean invoked = false;
        RuntimeException exceptionToThrow;

        @RedisCircuitBreaker
        public String execute() {
            invoked = true;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return "executed";
        }
    }
}
