package com.kazama.redis_cache_demo.seckill.repository;

import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;

import java.util.Optional;

public interface SeckillRepositoryCustom {

    Optional<SeckillActivityDTO> findSeckillActivityById(Long id);
}
