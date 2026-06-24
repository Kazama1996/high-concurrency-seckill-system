package com.kazama.redis_cache_demo.infra.outbox.config;

import com.kazama.redis_cache_demo.infra.schedule.job.OutboxPollingJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxQuartzConfig {



    @Bean
    public JobDetail outboxPoolingJobDetail(){
        return JobBuilder
                .newJob(OutboxPollingJob.class)
                .withIdentity("outboxPollingJob" , "outbox")
                .storeDurably()
                .build();

    }

    @Bean
    public Trigger outboxPollingTrigger(JobDetail outboxPoolingJobDetail){
        return TriggerBuilder.newTrigger()
                .forJob(outboxPoolingJobDetail)
                .withIdentity("outboxPollingTrigger","outbox")
                .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(5))
                .build();
    }
}
