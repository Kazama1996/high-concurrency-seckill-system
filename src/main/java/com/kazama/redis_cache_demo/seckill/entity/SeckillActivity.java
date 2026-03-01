package com.kazama.redis_cache_demo.seckill.entity;


import com.kazama.common.snowflake.SnowflakeId;
import com.kazama.redis_cache_demo.product.entity.Product;
import com.kazama.redis_cache_demo.seckill.enums.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "seckill_activities" ,indexes = {
        @Index(name = "idx_product_status" , columnList = "product_id , status"),
        @Index(name = "idx_start_time" , columnList = "start_time")
})
public class SeckillActivity {

    @Id
    @SnowflakeId
    private Long id;

    private Long productId;

    private BigDecimal seckillPrice;

    private Integer totalStock;

    private Integer remainingStock;

    private ZonedDateTime startTime;

    private ZonedDateTime endTime;

    @Enumerated(value = EnumType.STRING)
    private Status status;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
