package com.kazama.redis_cache_demo.product.repository.sql;

public final class ProductSql {

    private ProductSql() {};

    public static  final String GET_PRODUCT_BY_ID = """
        SELECT * FROM products WHERE id = :id
    """;

    public static final String UPDATE_SECKILL_BY_IDS = """
            UPDATE products SET is_seckill = true WHERE id IN (:ids)
        """;
}
