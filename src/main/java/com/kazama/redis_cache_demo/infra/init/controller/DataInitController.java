package com.kazama.redis_cache_demo.infra.init.controller;

import com.kazama.redis_cache_demo.infra.init.service.DataInitService;
import com.kazama.redis_cache_demo.seckill.service.SeckillActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dev/init")
@RequiredArgsConstructor
@Slf4j
@Profile({"dev","local"})
public class DataInitController {

    private final DataInitService dataInitService;

    private final SeckillActivityService seckillActivityService;


    @PostMapping("products/default")
    public ResponseEntity<Map<String,Object>> initDefaultProducts(){
        log.info("trigger default data initialization");

        long startTime = System.currentTimeMillis();
        List<Long> productIds = dataInitService.initDefaultData();
        long duration = System.currentTimeMillis() - startTime;

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Initialized 10 products. Use productIds to create seckill activities.",
                "duration", duration + "ms",
                "productIds", productIds
        ));

    }


    @PostMapping("/products")
    public ResponseEntity<Map<String,Object>> initProducts(
            @RequestParam(defaultValue = "1000") int total){

        log.info("Trigger custom size data initialization: total={}" , total);

        long startTime = System.currentTimeMillis();
        List<Long> productIds = dataInitService.initProducts(total);
        long duration = System.currentTimeMillis()-startTime;

        return ResponseEntity.ok(Map.of("success",true,
                "message" ,"success initialization" + total+ "products" ,
                "duration",duration+"ms",
                "productIds", productIds));

    }


    @DeleteMapping("/seckill/{activityId}/reset")
    public ResponseEntity<Map<String, Object>> resetSeckillActivity(@PathVariable Long activityId) {
        log.info("Reset seckill activity: {}", activityId);
        seckillActivityService.resetActivity(activityId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Activity " + activityId + " reset successfully"
        ));
    }

}
