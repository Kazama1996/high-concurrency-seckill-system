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
                                                     JobDetail[] jobDetails,
                                                     Trigger[] triggers) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(jobFactory);
        factory.setJobDetails(jobDetails);
        factory.setTriggers(triggers);
        return factory;
    }
    @Bean
    public Scheduler scheduler(SchedulerFactoryBean factory){
        return factory.getScheduler();
    }

}
