package com.kazama.redis_cache_demo.seckill.repository;

import com.kazama.redis_cache_demo.seckill.entity.SeckillActivity;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface SeckillActivityRepository extends JpaRepository<SeckillActivity , Long> , SeckillRepositoryCustom{



    @Query("""
      SELECT s FROM SeckillActivity s
      WHERE s.productId IN (:productIds)
      AND s.status NOT IN ('CANCELLED', 'ENDED')
      AND s.startTime <= :maxEndTime
      AND s.endTime >= :minStartTime
    """)
    List<SeckillActivity> findOverlappingActivities(
            @Param("productIds") List<Long> productIds ,
            @Param("minStartTime") ZonedDateTime minStartTime ,
            @Param("maxEndTime") ZonedDateTime maxEndTime);
}
