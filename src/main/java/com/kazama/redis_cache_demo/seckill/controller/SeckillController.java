package com.kazama.redis_cache_demo.seckill.controller;

import com.kazama.redis_cache_demo.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.naming.ServiceUnavailableException;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/seckill")
public class SeckillController {
    
    
    private final SeckillService seckillService;

    @PostMapping("/deduct")
    public ResponseEntity<?> seckill(@RequestParam Long userId, @RequestParam Long activityId) throws ServiceUnavailableException {
        long result = seckillService.deductStock(activityId, userId);
        return ResponseEntity.ok(result);
    }
}
