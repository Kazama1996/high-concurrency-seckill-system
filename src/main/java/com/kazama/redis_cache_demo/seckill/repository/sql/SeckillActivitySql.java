package com.kazama.redis_cache_demo.seckill.repository.sql;

public final  class SeckillActivitySql {


    private SeckillActivitySql() {};

    public static  final String GET_SECKILL_ACTIVITIES_BY_ID = """
        SELECT * FROM seckill_activities WHERE id = :id
    """;

}
