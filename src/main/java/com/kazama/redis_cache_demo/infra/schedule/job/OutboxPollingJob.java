package com.kazama.redis_cache_demo.infra.schedule.job;


import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import com.kazama.redis_cache_demo.order.repository.OrderCreatedOutboxRepository;
import com.kazama.redis_cache_demo.order.service.OutboxPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class OutboxPollingJob implements Job {


    private final OrderCreatedOutboxRepository orderCreatedOutboxRepository;

    private final OutboxPublisherService outboxPublisherService;


    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        List<OrderCreatedOutbox> pendingOutbox = orderCreatedOutboxRepository.findByStatus(OutboxStatus.PENDING);

        if(pendingOutbox.isEmpty()) return;

        pendingOutbox.forEach(outboxPublisherService::publish);

        log.info("OrderCreatedOutbox polling triggered, found {} pending records", pendingOutbox.size());

    }
}
