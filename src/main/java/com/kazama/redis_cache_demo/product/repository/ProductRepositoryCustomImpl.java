package com.kazama.redis_cache_demo.product.repository;

import com.kazama.redis_cache_demo.product.dto.ProductDTO;
import com.kazama.redis_cache_demo.product.repository.sql.ProductSql;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class ProductRepositoryCustomImpl implements  ProductRepositoryCustom {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Optional<ProductDTO> findProductDTOById(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        List<ProductDTO> results = namedParameterJdbcTemplate.query(
                ProductSql.GET_PRODUCT_BY_ID,
                params,
                new ProductRowMapper()
        );

        return results.stream().findFirst();
    }
}
