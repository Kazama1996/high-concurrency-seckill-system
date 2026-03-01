package com.kazama.redis_cache_demo.seckill.event;

import com.kazama.redis_cache_demo.infra.schedule.job.SeckillCacheWarmingJob;
import com.kazama.redis_cache_demo.product.events.ProductUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;
import java.util.Date;

@Component
@Slf4j
@RequiredArgsConstructor
public class SeckillActivityEventListener {

    private final Scheduler scheduler;

    @Value("${seckill.warmup.minutes-before:60}")
    private long minutesBefore;


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityCreated(SeckillActivityCreatedEvent event) throws SchedulerException {
        try {
            ZonedDateTime warmupTime = event.startTime().minusMinutes(minutesBefore);

            log.info("Registering Quartz trigger for activityId: {}, productId: {}, warmup at: {}",
                    event.activityId(),
                    event.productId(),
                    warmupTime
            );

            JobDetail jobDetail = JobBuilder.newJob(SeckillCacheWarmingJob.class)
                    .withIdentity("warmup-" + event.activityId(), "seckill")
                    .usingJobData("activityId", event.activityId())
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + event.activityId(), "seckill")
                    .startAt(Date.from(warmupTime.toInstant()))
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);

            log.info("Quartz trigger registered successfully for activityId: {}", event.activityId());

        } catch (SchedulerException e) {
            log.error("Failed to register Quartz trigger for activityId: {}", event.activityId(), e);
            throw new RuntimeException("Failed to schedule cache warming job", e);
        }
    }
}
