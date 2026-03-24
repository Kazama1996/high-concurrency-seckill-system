package com.kazama.redis_cache_demo.seckill.controller;

import com.kazama.redis_cache_demo.infra.circuitbreaker.annotation.RedisCircuitBreaker;
import com.kazama.redis_cache_demo.infra.ratelimit.RateLimit;
import com.kazama.redis_cache_demo.infra.ratelimit.RateLimitType;
import com.kazama.redis_cache_demo.order.entity.Orders;
import com.kazama.redis_cache_demo.seckill.dto.SeckillRequest;
import com.kazama.redis_cache_demo.seckill.service.SeckillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/seckill")
public class SeckillController {
    
    
    private final SeckillService seckillService;

    @PostMapping("/deduct")
    @RedisCircuitBreaker
    @RateLimit(key="'seckill:user:' + #request.userId() + ':activity:' + #request.activityId()", type = RateLimitType.SLIDING_WINDOW)
    public ResponseEntity<?> seckill(@Valid @RequestBody SeckillRequest request)  {
        Long orderId = seckillService.deductStock(request);
        return ResponseEntity.ok(orderId);
    }
}
