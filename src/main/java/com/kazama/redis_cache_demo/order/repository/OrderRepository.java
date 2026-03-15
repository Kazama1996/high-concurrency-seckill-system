package com.kazama.redis_cache_demo.order.repository;

import com.kazama.redis_cache_demo.order.entity.Orders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Orders,Long> {
}
