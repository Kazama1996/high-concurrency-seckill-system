package com.kazama.redis_cache_demo.seckill.repository;

import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.product.repository.ProductRowMapper;
import com.kazama.redis_cache_demo.product.repository.sql.ProductSql;
import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.repository.sql.SeckillActivitySql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class SeckillActivityRepositoryCustomImpl implements SeckillRepositoryCustom{

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;


    @Override
    public Optional<SeckillActivityDTO> findSeckillActivityById(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        List<SeckillActivityDTO> results = namedParameterJdbcTemplate.query(
                SeckillActivitySql.GET_SECKILL_ACTIVITIES_BY_ID,
                params,
                new SeckillActivityMapper()
        );

        return results.stream().findFirst();
    }
}
