package com.kazama.redis_cache_demo.infra.schedule.job;


import com.kazama.redis_cache_demo.infra.outbox.entity.Outbox;
import com.kazama.redis_cache_demo.infra.outbox.enums.OutboxStatus;
import com.kazama.redis_cache_demo.infra.outbox.repository.OutboxRepository;
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


    private final OutboxRepository outboxRepository;

    private final KafkaTemplate<String ,String> kafkaTemplate;


    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {



        List<Outbox> pendingOutbox = outboxRepository.findByStatus(OutboxStatus.PENDING);

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
                        outboxRepository.save(outbox);
                    });
        });

        log.info("OutboxPollingJob triggered, found {} pending records", pendingOutbox.size());



    }
}
