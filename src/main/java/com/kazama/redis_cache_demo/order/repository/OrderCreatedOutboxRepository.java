package com.kazama.redis_cache_demo.order.repository;

import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderCreatedOutboxRepository extends JpaRepository<OrderCreatedOutbox , Long> {

    List<OrderCreatedOutbox> findTop500ByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses);

    List<OrderCreatedOutbox> findByStatusAndUpdatedAtBefore(OutboxStatus status , Instant threshold);


    @Modifying(clearAutomatically = true)
    @Query("UPDATE OrderCreatedOutbox o SET o.status = :status WHERE o.id IN :ids")
    void bulkUpdateStatus(@Param("ids") List<Long> ids, @Param("status") OutboxStatus status);


    @Modifying(clearAutomatically = true)
    @Query(value = """
    UPDATE order_created_outbox
        SET retry_count = retry_count+1,
            status = CASE WHEN retry_count +1 >= :maxRetry THEN 'DEAD_LETTER' ELSE 'FAILED' END
        WHERE id IN :ids AND status <> 'DEAD_LETTER'
    """,nativeQuery = true)
    void bulkMarkFailed(@Param("ids") List<Long> ids , @Param("maxRetry") int maxRetry);



}
