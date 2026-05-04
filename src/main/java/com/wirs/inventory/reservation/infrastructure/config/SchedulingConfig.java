package com.wirs.inventory.reservation.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Enables scheduled task processing with a dedicated thread pool for the expiry job. */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /** Dedicated thread pool prevents expiry job from blocking request handling threads. */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("expiry-job-");
        return scheduler;
    }
}
