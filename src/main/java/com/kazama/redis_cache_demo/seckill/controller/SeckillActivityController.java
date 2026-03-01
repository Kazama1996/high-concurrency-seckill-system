package com.kazama.redis_cache_demo.seckill.controller;

import com.kazama.redis_cache_demo.seckill.dto.CreateSeckillActivityRequest;
import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.service.SeckillActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile({"dev","local"})
@RequestMapping("/api/v1/seckill/activities")
@RequiredArgsConstructor
public class SeckillActivityController {

    private final SeckillActivityService seckillActivityService;

    @PostMapping
    public ResponseEntity<?> createActivities(@RequestBody List<CreateSeckillActivityRequest> requests){

        List<SeckillActivityDTO> activities = seckillActivityService.createActivities(requests);

        return ResponseEntity.ok(activities);
    }
}
