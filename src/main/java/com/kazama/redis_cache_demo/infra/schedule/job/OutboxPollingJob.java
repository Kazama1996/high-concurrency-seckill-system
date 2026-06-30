package com.kazama.redis_cache_demo.infra.schedule.job;


import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import com.kazama.redis_cache_demo.order.repository.OrderCreatedOutboxRepository;
import com.kazama.redis_cache_demo.order.service.OutboxPublisherService;
import com.kazama.redis_cache_demo.order.service.OutboxStatusUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class OutboxPollingJob implements Job {


    private final OrderCreatedOutboxRepository orderCreatedOutboxRepository;

    private final OutboxPublisherService outboxPublisherService;

    private final OutboxStatusUpdateService statusUpdateService;

    private static final List<OutboxStatus> RETRYABLE_STATUSES = List.of(OutboxStatus.PENDING, OutboxStatus.FAILED);
    private static final Duration SENDING_TIMEOUT = Duration.ofMinutes(5);



    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        List<OrderCreatedOutbox> targets = new ArrayList<>(orderCreatedOutboxRepository.findTop500ByStatusInOrderByCreatedAtAsc(RETRYABLE_STATUSES));

        List<OrderCreatedOutbox> stuckSending = orderCreatedOutboxRepository
                .findByStatusAndUpdatedAtBefore(OutboxStatus.SENDING, Instant.now().minus(SENDING_TIMEOUT));

        if (!stuckSending.isEmpty()) {
            log.warn("Found {} outbox records stuck in SENDING, retrying", stuckSending.size());
            List<Long> stuckIds = stuckSending.stream().map(OrderCreatedOutbox::getId).toList();
            statusUpdateService.markFailedBatch(stuckIds, OutboxStatusUpdateService.MAX_RETRY_ATTEMPTS);

        }

        if (targets.isEmpty()) return;

        List<Long> ids = targets.stream().map(OrderCreatedOutbox::getId).toList();
        statusUpdateService.markAsSending(ids);

        targets.forEach(outboxPublisherService::publish);

        log.info("OrderCreatedOutbox polling triggered, dispatched {} records", targets.size());


    }
}
