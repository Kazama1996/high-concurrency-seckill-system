package com.kazama.redis_cache_demo.infra.diagnostic.seckill.controller;


import com.kazama.redis_cache_demo.infra.diagnostic.seckill.dto.SeckillStockOrderDiagnosticResponse;
import com.kazama.redis_cache_demo.infra.diagnostic.seckill.service.DiagnosticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dev/diagnostic/seckill")
@Profile({"local","dev"})
@Slf4j
@RequiredArgsConstructor
public class DiagnosticController {


    private final DiagnosticService diagnosticService;

    @GetMapping("/{activityId}")
    public ResponseEntity<SeckillStockOrderDiagnosticResponse> diagnoseSeckillStock(@PathVariable Long activityId){
        log.debug("call API diagnoseSeckillStock activityId: {}" , activityId);

        SeckillStockOrderDiagnosticResponse responseBody = diagnosticService.diagnoseSeckillStock(activityId);

        return ResponseEntity.ok(responseBody);

    }




}
