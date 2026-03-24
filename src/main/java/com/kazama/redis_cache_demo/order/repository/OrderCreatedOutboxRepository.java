package com.kazama.redis_cache_demo.order.repository;

import com.kazama.redis_cache_demo.infra.outbox.entity.Outbox;
import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderCreatedOutboxRepository extends JpaRepository<OrderCreatedOutbox , Long> {

    List<OrderCreatedOutbox> findByStatus(OutboxStatus status);

}
