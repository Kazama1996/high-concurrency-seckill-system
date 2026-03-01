package com.kazama.redis_cache_demo.seckill.repository;

import com.kazama.redis_cache_demo.seckill.dto.SeckillActivityDTO;
import com.kazama.redis_cache_demo.seckill.enums.Status;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

public class SeckillActivityMapper implements RowMapper<SeckillActivityDTO> {
    @Override
    public SeckillActivityDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime odtStart =  rs.getObject("start_time", OffsetDateTime.class);
        ZonedDateTime startTime = odtStart != null ? odtStart.toZonedDateTime() : null;


        OffsetDateTime odtEnd =  rs.getObject("end_time", OffsetDateTime.class);
        ZonedDateTime endTime = odtEnd != null ? odtEnd.toZonedDateTime() : null;


        return new SeckillActivityDTO(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getBigDecimal("seckill_price"),
                rs.getInt("total_stock"),
                rs.getInt("remaining_stock"),
                startTime,
                endTime,
                Status.valueOf(rs.getString("status"))
        );
    }
}
