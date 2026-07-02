package com.kazama.redis_cache_demo.order.service;

import com.kazama.redis_cache_demo.AbstractIntegrationTest;
import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.infra.schedule.job.OutboxPollingJob;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import com.kazama.redis_cache_demo.order.repository.OrderCreatedOutboxRepository;
import com.kazama.redis_cache_demo.seckill.kafka.config.KafkaTopicConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

class OutboxPollingIntegrationTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OrderCreatedOutboxRepository repository;

    @Autowired
    private OutboxPublisherService publisherService;

    @Autowired
    private OutboxStatusUpdateService statusUpdateService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OutboxPollingJob job;

    private static final AtomicLong ORDER_ID_SEQ = new AtomicLong(1_000_000);

    @BeforeEach
    void setUp() {
        job = new OutboxPollingJob(repository, publisherService, statusUpdateService);
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll();
        Mockito.reset(kafkaTemplate);
    }

    // --- helpers ---

    private OrderCreatedOutbox saveOutbox(OutboxStatus status) {
        return saveOutbox(status, 0);
    }

    private OrderCreatedOutbox saveOutbox(OutboxStatus status, int retryCount) {
        OrderCreatedOutbox outbox = OrderCreatedOutbox.builder()
                .orderId(ORDER_ID_SEQ.getAndIncrement())
                .topicName(KafkaTopicConfig.ORDER_NOTIFICATION_TOPIC_NAME)
                .payload("{\"id\":1,\"userId\":1,\"seckillActivityId\":1,\"productId\":1," +
                         "\"quantity\":1,\"originalPrice\":100,\"seckillPrice\":9}")
                .status(status)
                .retryCount(retryCount)
                .build();
        return repository.saveAndFlush(outbox);
    }

    private void backdateUpdatedAt(Long id, Duration ago) {
        jdbcTemplate.update(
                "UPDATE order_created_outbox SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(ago)),
                id
        );
    }

    private CompletableFuture<SendResult<String, String>> failedFuture() {
        CompletableFuture<SendResult<String, String>> f = new CompletableFuture<>();
        f.completeExceptionally(new RuntimeException("simulated Kafka send failure"));
        return f;
    }

    // --- Scenario 1: PENDING → SENT ---

    @Test
    void pendingRecord_sendSucceeds_markedSent() throws JobExecutionException {
        OrderCreatedOutbox outbox = saveOutbox(OutboxStatus.PENDING);

        job.execute(null);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            OrderCreatedOutbox updated = repository.findById(outbox.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(updated.getRetryCount()).isZero();
        });
    }

    // --- Scenario 2: PENDING → FAILED(retryCount+1) → retry poll → SENT ---

    @Test
    void failedSend_incrementsRetryCount_retriedOnNextPoll() throws JobExecutionException {
        OrderCreatedOutbox outbox = saveOutbox(OutboxStatus.PENDING);

        doReturn(failedFuture()).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        job.execute(null);

        // send() returns a pre-completed future → whenComplete fires synchronously → DB updated before execute() returns
        OrderCreatedOutbox afterFirstPoll = repository.findById(outbox.getId()).orElseThrow();
        assertThat(afterFirstPoll.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(afterFirstPoll.getRetryCount()).isEqualTo(1);

        Mockito.reset(kafkaTemplate); // restore real Kafka sends

        job.execute(null);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            OrderCreatedOutbox updated = repository.findById(outbox.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OutboxStatus.SENT);
        });
    }

    // --- Scenario 3: Stuck SENDING → FAILED(+1) / fresh SENDING untouched → retry poll → SENT ---

    @Test
    void stuckSending_promotedToFailed_freshSendingUntouched_thenRetriedAndSent() throws JobExecutionException {
        // Stuck record: updatedAt is 6 min in the past, past the 5-min SENDING_TIMEOUT
        OrderCreatedOutbox stuck = saveOutbox(OutboxStatus.SENDING);
        backdateUpdatedAt(stuck.getId(), Duration.ofMinutes(6));

        // Fresh record: updatedAt = now (set by @UpdateTimestamp on save) → within timeout window
        OrderCreatedOutbox fresh = saveOutbox(OutboxStatus.SENDING);

        job.execute(null); // poll 1: detects stuck, promotes it to FAILED; fresh is untouched

        OrderCreatedOutbox stuckAfterPoll1 = repository.findById(stuck.getId()).orElseThrow();
        assertThat(stuckAfterPoll1.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(stuckAfterPoll1.getRetryCount()).isEqualTo(1);

        OrderCreatedOutbox freshAfterPoll1 = repository.findById(fresh.getId()).orElseThrow();
        assertThat(freshAfterPoll1.getStatus()).isEqualTo(OutboxStatus.SENDING);
        assertThat(freshAfterPoll1.getRetryCount()).isZero();

        job.execute(null); // poll 2: stuck (now FAILED) is picked up and sent; fresh remains SENDING

        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(repository.findById(stuck.getId()).orElseThrow().getStatus())
                    .isEqualTo(OutboxStatus.SENT);
        });

        // fresh SENDING record is never touched: not in RETRYABLE_STATUSES and not past SENDING_TIMEOUT
        assertThat(repository.findById(fresh.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.SENDING);
    }

    // --- Scenario 4: retryCount at MAX-1 → DEAD_LETTER on next failure → terminal ---

    @Test
    void maxRetryExceeded_becomesDeadLetter_neverRetriedAgain() throws JobExecutionException {
        // retryCount = 4 (MAX_RETRY - 1), so the next bulkMarkFailed call → retryCount 5 >= 5 → DEAD_LETTER
        OrderCreatedOutbox outbox = saveOutbox(OutboxStatus.FAILED, OutboxStatusUpdateService.MAX_RETRY_ATTEMPTS - 1);

        doReturn(failedFuture()).when(kafkaTemplate).send(anyString(), anyString(), anyString());

        job.execute(null);

        OrderCreatedOutbox deadLettered = repository.findById(outbox.getId()).orElseThrow();
        assertThat(deadLettered.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(deadLettered.getRetryCount()).isEqualTo(OutboxStatusUpdateService.MAX_RETRY_ATTEMPTS);

        Mockito.reset(kafkaTemplate); // restore real sends — DEAD_LETTER must never be published

        job.execute(null); // another poll: DEAD_LETTER is not in RETRYABLE_STATUSES, not a stuck SENDING

        OrderCreatedOutbox stillDead = repository.findById(outbox.getId()).orElseThrow();
        assertThat(stillDead.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(stillDead.getRetryCount()).isEqualTo(OutboxStatusUpdateService.MAX_RETRY_ATTEMPTS);
    }
}
