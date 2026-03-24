package com.kazama.redis_cache_demo.infra.schedule.job;


import com.kazama.redis_cache_demo.infra.outbox.entity.Outbox;
import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.infra.outbox.repository.OutboxRepository;
import com.kazama.redis_cache_demo.order.entity.OrderCreatedOutbox;
import com.kazama.redis_cache_demo.order.repository.OrderCreatedOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class OutboxPollingJob implements Job {


    private final OrderCreatedOutboxRepository orderCreatedOutboxRepository;

    private final KafkaTemplate<String ,String> kafkaTemplate;


    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {



        List<OrderCreatedOutbox> pendingOutbox = orderCreatedOutboxRepository.findByStatus(OutboxStatus.PENDING);

        if(pendingOutbox.isEmpty()) return;

        pendingOutbox.forEach(outbox -> {
            kafkaTemplate.send(outbox.getTopicName(), outbox.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send outbox message, id: {}", outbox.getId(), ex);
                            outbox.setStatus(OutboxStatus.FAILED);
                        } else {
                            outbox.setStatus(OutboxStatus.SENT);
                        }
                        orderCreatedOutboxRepository.save(outbox);
                    });
        });

        log.info("OrderCreatedOutbox polling triggered, found {} pending records", pendingOutbox.size());



    }
}
