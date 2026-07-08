package com.kazama.redis_cache_demo.order.repository;

import com.kazama.redis_cache_demo.AbstractPostgresIntegrationTest;
import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCreatedOutboxRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final int MAX_RETRY = 5;
    private static final AtomicLong ID_SEQ = new AtomicLong(3_000_000);
    private static final AtomicLong ORDER_ID_SEQ = new AtomicLong(3_000_000);

    @Autowired
    private OrderCreatedOutboxRepository repository;

    private OrderCreatedOutbox seed(OutboxStatus status, int retryCount) {
        OrderCreatedOutbox outbox = OrderCreatedOutbox.builder()
                .id(ID_SEQ.getAndIncrement())
                .orderId(ORDER_ID_SEQ.getAndIncrement())
                .topicName("order.created")
                .payload("{}")
                .status(status)
                .retryCount(retryCount)
                .build();
        return repository.saveAndFlush(outbox);
    }

    @Test
    void justUnderBoundary_retryCountThreeToFour_staysFailed() {
        OrderCreatedOutbox outbox = seed(OutboxStatus.FAILED, 3);

        repository.bulkMarkFailed(List.of(outbox.getId()), MAX_RETRY);

        OrderCreatedOutbox updated = repository.findById(outbox.getId()).orElseThrow();
        assertThat(updated.getRetryCount()).isEqualTo(4);
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void exactBoundary_retryCountFourToFive_becomesDeadLetter() {
        OrderCreatedOutbox outbox = seed(OutboxStatus.FAILED, 4);

        repository.bulkMarkFailed(List.of(outbox.getId()), MAX_RETRY);

        OrderCreatedOutbox updated = repository.findById(outbox.getId()).orElseThrow();
        assertThat(updated.getRetryCount()).isEqualTo(5);
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
    }

    @Test
    void alreadyPastMax_retryCountTenToEleven_staysDeadLetter() {
        OrderCreatedOutbox outbox = seed(OutboxStatus.DEAD_LETTER, 10);

        repository.bulkMarkFailed(List.of(outbox.getId()), MAX_RETRY);

        OrderCreatedOutbox updated = repository.findById(outbox.getId()).orElseThrow();
        assertThat(updated.getRetryCount()).isEqualTo(11);
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
    }

    @Test
    void mixedBatch_eachRowLandsOnItsOwnOutcome() {
        OrderCreatedOutbox justUnder = seed(OutboxStatus.FAILED, 3);
        OrderCreatedOutbox exact = seed(OutboxStatus.FAILED, 4);
        OrderCreatedOutbox pastMax = seed(OutboxStatus.DEAD_LETTER, 10);

        repository.bulkMarkFailed(
                List.of(justUnder.getId(), exact.getId(), pastMax.getId()), MAX_RETRY);

        assertThat(repository.findById(justUnder.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.FAILED);
        assertThat(repository.findById(exact.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(repository.findById(pastMax.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.DEAD_LETTER);
    }

    // Known gap: bulkMarkFailed's WHERE clause is only `id IN :ids`, with no status guard, so
    // calling it again on a row already in the terminal DEAD_LETTER state still increments
    // retry_count further. This documents existing behavior; it is not asserting a spec
    // requirement, and the gap itself is intentionally left unfixed here (see plan discussion).
    @Test
    void bulkMarkFailed_calledAgainOnDeadLetterRow_knownGap_stillIncrementsRetryCount() {
        OrderCreatedOutbox outbox = seed(OutboxStatus.DEAD_LETTER, 5);

        repository.bulkMarkFailed(List.of(outbox.getId()), MAX_RETRY);

        OrderCreatedOutbox updated = repository.findById(outbox.getId()).orElseThrow();
        assertThat(updated.getRetryCount()).isEqualTo(6);
        assertThat(updated.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
    }

    @Test
    void emptyIdsList_isNoOp() {
        OrderCreatedOutbox outbox = seed(OutboxStatus.FAILED, 3);

        repository.bulkMarkFailed(List.of(), MAX_RETRY);

        OrderCreatedOutbox unchanged = repository.findById(outbox.getId()).orElseThrow();
        assertThat(unchanged.getRetryCount()).isEqualTo(3);
        assertThat(unchanged.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }
}
