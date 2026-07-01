package com.kazama.redis_cache_demo.infra.outbox.config;

import com.kazama.redis_cache_demo.infra.schedule.job.OutboxPollingJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxQuartzConfig {



    @Bean
    public JobDetail outboxPollingJobDetail(){
        return JobBuilder
                .newJob(OutboxPollingJob.class)
                .withIdentity("outboxPollingJob" , "outbox")
                .storeDurably()
                .build();

    }

    @Bean
    public Trigger outboxPollingTrigger(JobDetail outboxPollingJobDetail){
        return TriggerBuilder.newTrigger()
                .forJob(outboxPollingJobDetail)
                .withIdentity("outboxPollingTrigger","outbox")
                .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(5))
                .build();
    }
}
