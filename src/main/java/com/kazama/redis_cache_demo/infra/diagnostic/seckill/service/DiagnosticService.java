package com.kazama.redis_cache_demo.infra.diagnostic.seckill.service;

import com.kazama.redis_cache_demo.infra.diagnostic.seckill.dto.SeckillStockOrderDiagnosticResponse;
import com.kazama.redis_cache_demo.order.repository.OrderRepository;
import com.kazama.redis_cache_demo.seckill.entity.SeckillActivity;
import com.kazama.redis_cache_demo.seckill.exception.SeckillActivityNotFoundException;
import com.kazama.redis_cache_demo.seckill.repository.SeckillActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiagnosticService {

    private final OrderRepository orderRepository;

    private final SeckillActivityRepository seckillActivityRepository;


    public SeckillStockOrderDiagnosticResponse diagnoseSeckillStock(Long activityId){
        log.debug("start to query orders and seckill activity total stock by activityId : {}", activityId);

        Integer countByActivityId = orderRepository.findCountByActivityId(activityId);

        SeckillActivity seckillActivity = seckillActivityRepository.findById(activityId).orElseThrow(()-> new SeckillActivityNotFoundException(String.format("seckillActivity is not found:%d",activityId)));

        Integer totalStock = seckillActivity.getTotalStock();
        boolean isValid = totalStock >= countByActivityId;

        return new SeckillStockOrderDiagnosticResponse(totalStock , countByActivityId , isValid);

    }
}
