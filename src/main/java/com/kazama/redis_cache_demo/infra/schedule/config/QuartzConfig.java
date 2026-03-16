package com.kazama.redis_cache_demo.infra.schedule.config;

import com.kazama.redis_cache_demo.infra.schedule.job.OutboxPollingJob;
import org.quartz.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
public class QuartzConfig {


    @Bean
    public SpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext){
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(SpringBeanJobFactory jobFactory,
                                                     JobDetail outboxPollingJobDetail,
                                                     Trigger outboxPollingTrigger) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(jobFactory);
        factory.setJobDetails(outboxPollingJobDetail);
        factory.setTriggers(outboxPollingTrigger);
        return factory;
    }
    @Bean
    public Scheduler scheduler(SchedulerFactoryBean factory){
        return factory.getScheduler();
    }


    @Bean
    public JobDetail outboxPollingJobDetail() {
        return JobBuilder.newJob(OutboxPollingJob.class)
                .withIdentity("outboxPollingJob", "outbox")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger outboxPollingTrigger(JobDetail outboxPollingJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(outboxPollingJobDetail)
                .withIdentity("outboxPollingTrigger", "outbox")
                .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(5))
                .build();
    }



}
