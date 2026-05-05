package com.wirs.inventory.reservation.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables scheduled task processing with a dedicated thread pool.
 *
 * Isolates scheduled jobs (e.g., {@link com.wirs.inventory.reservation.application.job.ReservationExpiryJob})
 * on their own thread pool so that long-running or stuck tasks do not starve
 * the request-handling thread pool.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * Creates a thread pool with 2 threads dedicated to scheduled tasks.
     * Threads are named with the {@code expiry-job-} prefix for easy
     * identification in thread dumps.
     *
     * @return a configured {@link ThreadPoolTaskScheduler}
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("expiry-job-");
        return scheduler;
    }
}
