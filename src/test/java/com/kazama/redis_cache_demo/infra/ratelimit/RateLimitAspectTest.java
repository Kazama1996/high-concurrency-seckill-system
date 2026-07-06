package com.kazama.redis_cache_demo.infra.ratelimit;

import com.kazama.redis_cache_demo.infra.exception.RateLimitExceedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class RateLimitAspectTest {

    @Mock private RateLimiter rateLimiter;

    private RateLimitAspect aspect;
    private TestTarget proxy;

    @BeforeEach
    void setUp() {
        when(rateLimiter.supportedType()).thenReturn(RateLimitType.SLIDING_WINDOW);
        aspect = new RateLimitAspect(List.of(rateLimiter));

        AspectJProxyFactory factory = new AspectJProxyFactory(new TestTarget());
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    @Test
    void rateLimitAllowed_proceedsToMethod_returnsResult() {
        when(rateLimiter.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(true);

        String result = proxy.execute();

        assertEquals("executed", result);
    }

    @Test
    void rateLimitExceeded_throwsRateLimitExceedException() {
        when(rateLimiter.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(false);

        assertThrows(RateLimitExceedException.class, proxy::execute);
    }

    // Minimal test target — annotated so the aspect can intercept it
    static class TestTarget {
        @RateLimit(key = "'test:key'", type = RateLimitType.SLIDING_WINDOW, limit = 5, window = 60)
        public String execute() {
            return "executed";
        }
    }
}
