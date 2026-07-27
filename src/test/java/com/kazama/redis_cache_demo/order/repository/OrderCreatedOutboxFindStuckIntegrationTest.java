package com.kazama.redis_cache_demo.order.repository;

import com.kazama.common.snowflake.SnowflakeGenerator;
import com.kazama.redis_cache_demo.AbstractPostgresIntegrationTest;
import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCreatedOutboxFindStuckIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderCreatedOutboxRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SnowflakeGenerator snowflakeGenerator;

    // Truncated to microseconds (Postgres timestamptz precision) so the value written via raw
    // JDBC and the value bound into the repository query compare exactly, with no sub-microsecond
    // rounding artifacts affecting the strict `<` boundary being tested.
    private OrderCreatedOutbox seedWithUpdatedAt(OutboxStatus status, Instant updatedAt) {
        OrderCreatedOutbox outbox = OrderCreatedOutbox.builder()
                .orderId(snowflakeGenerator.nextId())
                .topicName("order.created")
                .payload("{}")
                .status(status)
                .retryCount(0)
                .build();
        OrderCreatedOutbox saved = repository.saveAndFlush(outbox);
        jdbcTemplate.update(
                "UPDATE order_created_outbox SET updated_at = ? WHERE id = ?",
                Timestamp.from(updatedAt), saved.getId());
        return saved;
    }

    private static Instant threshold() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    void rowBeforeThreshold_sendingStatus_isIncluded() {
        Instant threshold = threshold();
        OrderCreatedOutbox before = seedWithUpdatedAt(OutboxStatus.SENDING, threshold.minusSeconds(1));

        List<OrderCreatedOutbox> result =
                repository.findByStatusAndUpdatedAtBefore(OutboxStatus.SENDING, threshold);

        assertThat(result).extracting(OrderCreatedOutbox::getId).containsExactly(before.getId());
    }

    @Test
    void rowExactlyAtThreshold_sendingStatus_isExcluded() {
        Instant threshold = threshold();
        seedWithUpdatedAt(OutboxStatus.SENDING, threshold);

        List<OrderCreatedOutbox> result =
                repository.findByStatusAndUpdatedAtBefore(OutboxStatus.SENDING, threshold);

        assertThat(result).isEmpty();
    }

    @Test
    void rowAfterThreshold_sendingStatus_isExcluded() {
        Instant threshold = threshold();
        seedWithUpdatedAt(OutboxStatus.SENDING, threshold.plusSeconds(1));

        List<OrderCreatedOutbox> result =
                repository.findByStatusAndUpdatedAtBefore(OutboxStatus.SENDING, threshold);

        assertThat(result).isEmpty();
    }

    @Test
    void rowBeforeThreshold_butWrongStatus_isExcluded() {
        Instant threshold = threshold();
        seedWithUpdatedAt(OutboxStatus.SENT, threshold.minusSeconds(1));

        List<OrderCreatedOutbox> result =
                repository.findByStatusAndUpdatedAtBefore(OutboxStatus.SENDING, threshold);

        assertThat(result).isEmpty();
    }

    @Test
    void combinedDataset_returnsExactlyTheStuckSendingRow() {
        Instant threshold = threshold();
        OrderCreatedOutbox stuck = seedWithUpdatedAt(OutboxStatus.SENDING, threshold.minusSeconds(1));
        seedWithUpdatedAt(OutboxStatus.SENDING, threshold);
        seedWithUpdatedAt(OutboxStatus.SENDING, threshold.plusSeconds(1));
        seedWithUpdatedAt(OutboxStatus.SENT, threshold.minusSeconds(1));

        List<OrderCreatedOutbox> result =
                repository.findByStatusAndUpdatedAtBefore(OutboxStatus.SENDING, threshold);

        assertThat(result).extracting(OrderCreatedOutbox::getId).containsExactly(stuck.getId());
    }
}
