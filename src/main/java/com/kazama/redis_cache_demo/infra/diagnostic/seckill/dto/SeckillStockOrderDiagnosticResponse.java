package com.kazama.redis_cache_demo.infra.diagnostic.seckill.dto;

public record SeckillStockOrderDiagnosticResponse(int totalStock , int ordersCreated , boolean isValid) {
}
