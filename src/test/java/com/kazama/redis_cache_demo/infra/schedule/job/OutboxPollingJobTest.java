package com.kazama.redis_cache_demo.infra.schedule.job;

import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import com.kazama.redis_cache_demo.order.repository.OrderCreatedOutboxRepository;
import com.kazama.redis_cache_demo.order.service.OutboxPublisherService;
import com.kazama.redis_cache_demo.order.service.OutboxStatusUpdateService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class OutboxPollingJobTest {

    @Mock private OrderCreatedOutboxRepository outboxRepository;
    @Mock private OutboxPublisherService outboxPublisherService;
    @Mock private OutboxStatusUpdateService statusUpdateService;
    @Mock private JobExecutionContext context;

    @InjectMocks private OutboxPollingJob outboxPollingJob;

    private OrderCreatedOutbox outbox(Long id) {
        return OrderCreatedOutbox.builder()
                .id(id).orderId(id).topicName("topic").payload("{}")
                .status(OutboxStatus.PENDING)
                .build();
    }


    @Test
    void execute_allSourcesEmpty_doesNothing() throws JobExecutionException {
        when(outboxRepository.claimPendingBatch(anyInt())).thenReturn(List.of());
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.SENDING),any(Instant.class))).thenReturn(List.of());

        outboxPollingJob.execute(context);

        verifyNoInteractions(statusUpdateService);
        verifyNoInteractions(outboxPublisherService);

    }

    @Test
    void execute_stuckSendingFound_marksFailedBatch() throws JobExecutionException {
        OrderCreatedOutbox stuck = outbox(1L);
        when(outboxRepository.claimPendingBatch(anyInt())).thenReturn(List.of());
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.SENDING), any(Instant.class)))
                .thenReturn(List.of(stuck));

        outboxPollingJob.execute(context);

        verify(statusUpdateService).markFailedBatch(List.of(1L), OutboxStatusUpdateService.MAX_RETRY_ATTEMPTS);
        verifyNoInteractions(outboxPublisherService);

    }

    @Test
    void execute_nonEmptyTargets_claimsBeforePublishing() throws JobExecutionException {
        OrderCreatedOutbox target = outbox(2L);
        when(outboxRepository.claimPendingBatch(anyInt())).thenReturn(List.of(target));
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.SENDING), any(Instant.class)))
                .thenReturn(List.of());

        outboxPollingJob.execute(context);

        InOrder inOrder = inOrder(outboxRepository, outboxPublisherService);
        inOrder.verify(outboxRepository).claimPendingBatch(anyInt());
        inOrder.verify(outboxPublisherService).publish(target);
    }

    @Test
    void execute_multipleTargets_publishesEachExactlyOnce() throws JobExecutionException {
        OrderCreatedOutbox target1 = outbox(2L);
        OrderCreatedOutbox target2 = outbox(3L);
        when(outboxRepository.claimPendingBatch(anyInt()))
                .thenReturn(List.of(target1, target2));
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.SENDING), any(Instant.class)))
                .thenReturn(List.of());

        outboxPollingJob.execute(context);

        verify(outboxPublisherService).publish(target1);
        verify(outboxPublisherService).publish(target2);
        verify(outboxPublisherService, times(2)).publish(any());
    }

    @Test
    void execute_stuckSendingAndTargetsBothPresent_bothBranchesFire() throws JobExecutionException {
        OrderCreatedOutbox stuck = outbox(1L);
        OrderCreatedOutbox target = outbox(2L);
        when(outboxRepository.claimPendingBatch(anyInt())).thenReturn(List.of(target));
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.SENDING), any(Instant.class)))
                .thenReturn(List.of(stuck));

        outboxPollingJob.execute(context);

        verify(statusUpdateService).markFailedBatch(List.of(1L), OutboxStatusUpdateService.MAX_RETRY_ATTEMPTS);
        verify(outboxPublisherService).publish(target);
    }

    @Test
    void execute_computesStuckSendingCutoffAsFiveMinutesAgo() throws JobExecutionException {
        when(outboxRepository.claimPendingBatch(anyInt())).thenReturn(List.of());
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        when(outboxRepository.findByStatusAndUpdatedAtBefore(eq(OutboxStatus.SENDING), cutoffCaptor.capture()))
                .thenReturn(List.of());

        Instant before = Instant.now();
        outboxPollingJob.execute(context);
        Instant after = Instant.now();

        Instant cutoff = cutoffCaptor.getValue();
        assertFalse(cutoff.isBefore(before.minusSeconds(300)));
        assertFalse(cutoff.isAfter(after.minusSeconds(300)));
    }
}
