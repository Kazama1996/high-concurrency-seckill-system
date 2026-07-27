package com.kazama.redis_cache_demo.order.repository;

import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderCreatedOutboxRepository extends JpaRepository<OrderCreatedOutbox , Long> {

    List<OrderCreatedOutbox> findByStatusAndUpdatedAtBefore(OutboxStatus status , Instant threshold);

    // Deliberately not @Modifying: @Modifying routes execution through executeUpdate(), which
    // discards any RETURNING result set. Leaving this as a plain query lets Spring Data call
    // getResultList() instead, which the Postgres driver executes as a query and maps
    // `RETURNING o.*` back onto OrderCreatedOutbox. FOR UPDATE SKIP LOCKED inside the CTE makes
    // concurrent callers (e.g. multiple Quartz instances) claim disjoint row sets, and folding
    // the SELECT + UPDATE into one statement removes the gap where two callers could both read
    // the same batch before either one marks it SENDING.
    @Transactional
    @Query(value = """
        WITH claimed AS (
            SELECT id FROM order_created_outbox
            WHERE status IN ('PENDING', 'FAILED')
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        )
        UPDATE order_created_outbox o
        SET status = 'SENDING', updated_at = now()
        FROM claimed c
        WHERE o.id = c.id
        RETURNING o.*
        """, nativeQuery = true)
    List<OrderCreatedOutbox> claimPendingBatch(@Param("limit") int limit);


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
