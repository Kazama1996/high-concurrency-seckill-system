package com.kazama.redis_cache_demo.order.repository;

import com.kazama.common.snowflake.SnowflakeGenerator;
import com.kazama.redis_cache_demo.AbstractPostgresIntegrationTest;
import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// Overrides @DataJpaTest's default (rollback-per-test, single shared connection/transaction):
// the concurrency test below needs seeded rows actually committed so two independent DB
// connections/transactions can race for them via FOR UPDATE SKIP LOCKED. NOT_SUPPORTED suspends
// any ambient transaction, so every repository call (seed and claim alike) commits immediately
// on its own connection.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderCreatedOutboxClaimPendingBatchIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderCreatedOutboxRepository repository;

    @Autowired
    private SnowflakeGenerator snowflakeGenerator;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
    }

    private OrderCreatedOutbox seed(OutboxStatus status) {
        OrderCreatedOutbox outbox = OrderCreatedOutbox.builder()
                .orderId(snowflakeGenerator.nextId())
                .topicName("order.created")
                .payload("{}")
                .status(status)
                .retryCount(0)
                .build();
        return repository.saveAndFlush(outbox);
    }

    @Test
    void claimsOnlyPendingAndFailedRows_andMarksThemSending() {
        OrderCreatedOutbox pending = seed(OutboxStatus.PENDING);
        OrderCreatedOutbox failed = seed(OutboxStatus.FAILED);
        OrderCreatedOutbox sent = seed(OutboxStatus.SENT);
        OrderCreatedOutbox sending = seed(OutboxStatus.SENDING);
        OrderCreatedOutbox deadLetter = seed(OutboxStatus.DEAD_LETTER);

        List<OrderCreatedOutbox> claimed = repository.claimPendingBatch(10);

        assertThat(claimed).extracting(OrderCreatedOutbox::getId)
                .containsExactlyInAnyOrder(pending.getId(), failed.getId());
        assertThat(claimed).allMatch(o -> o.getStatus() == OutboxStatus.SENDING);

        assertThat(repository.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENDING);
        assertThat(repository.findById(failed.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENDING);
        assertThat(repository.findById(sent.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(repository.findById(sending.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENDING);
        assertThat(repository.findById(deadLetter.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
    }

    @Test
    void respectsLimit_leavingRemainderUnclaimed() {
        List<Long> seededIds = IntStream.range(0, 5)
                .mapToObj(i -> seed(OutboxStatus.PENDING).getId())
                .toList();

        List<OrderCreatedOutbox> claimed = repository.claimPendingBatch(3);

        assertThat(claimed).hasSize(3);
        long stillPending = seededIds.stream()
                .filter(id -> repository.findById(id).orElseThrow().getStatus() == OutboxStatus.PENDING)
                .count();
        assertThat(stillPending).isEqualTo(2);
    }

    @Test
    void concurrentClaims_produceDisjointClaims_andClaimEveryPendingRowExactlyOnce() throws Exception {
        int total = 200;
        Set<Long> seededIds = new HashSet<>(
                IntStream.range(0, total)
                        .mapToObj(i -> seed(OutboxStatus.PENDING).getId())
                        .toList());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<List<Long>> claimTask = () -> {
            ready.countDown();
            start.await();
            return repository.claimPendingBatch(total).stream()
                    .map(OrderCreatedOutbox::getId)
                    .toList();
        };

        try {
            Future<List<Long>> f1 = pool.submit(claimTask);
            Future<List<Long>> f2 = pool.submit(claimTask);

            ready.await();
            start.countDown();

            List<Long> claimedByThread1 = f1.get(10, TimeUnit.SECONDS);
            List<Long> claimedByThread2 = f2.get(10, TimeUnit.SECONDS);

            Set<Long> set1 = new HashSet<>(claimedByThread1);
            Set<Long> set2 = new HashSet<>(claimedByThread2);

            assertThat(set1).doesNotContainAnyElementsOf(set2);

            Set<Long> union = new HashSet<>(set1);
            union.addAll(set2);
            assertThat(union).isEqualTo(seededIds);

            assertThat(seededIds)
                    .allMatch(id -> repository.findById(id).orElseThrow().getStatus() == OutboxStatus.SENDING);
        } finally {
            pool.shutdown();
        }
    }
}
