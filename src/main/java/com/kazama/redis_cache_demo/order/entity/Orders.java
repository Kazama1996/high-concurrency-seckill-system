package com.kazama.redis_cache_demo.order.entity;

import com.kazama.common.snowflake.SnowflakeId;
import com.kazama.redis_cache_demo.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Orders  {

    @Id
    @SnowflakeId
    private Long id;

    private Long userId;

    private Long productId;

    private Long seckillActivityId;

    private Integer quantity;

    private BigDecimal originalPrice;

    private BigDecimal seckillPrice;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "order_status", columnDefinition = "order_status")
    private OrderStatus orderStatus;


    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

}
