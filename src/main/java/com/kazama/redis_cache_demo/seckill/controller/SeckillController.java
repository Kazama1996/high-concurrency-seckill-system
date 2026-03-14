package com.kazama.redis_cache_demo.seckill.controller;

import com.kazama.redis_cache_demo.seckill.dto.SeckillRequest;
import com.kazama.redis_cache_demo.seckill.service.SeckillService;
import jakarta.validation.Valid;
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
    public ResponseEntity<?> seckill(@Valid @RequestBody SeckillRequest request) throws ServiceUnavailableException {
        long result = seckillService.deductStock(request);
        return ResponseEntity.ok(result);
    }
}
