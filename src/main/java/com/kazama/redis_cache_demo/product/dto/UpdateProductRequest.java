package com.kazama.redis_cache_demo.product.dto;

import java.math.BigDecimal;

public record UpdateProductRequest(
        String name,
        String description,
        BigDecimal price,
        String imageUrl
) {}
