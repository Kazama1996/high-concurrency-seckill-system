package com.kazama.redis_cache_demo.product.repository;

import com.kazama.redis_cache_demo.product.dto.ProductDTO;

import java.util.Optional;

public interface ProductRepositoryCustom {


    Optional<ProductDTO> findProductDTOById(Long id);
}
