package com.wirs.inventory.reservation.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures Caffeine-based in-memory caching with explicit TTLs per cache name. */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Creates a Caffeine-based cache manager with a 30-second write expiry.
     *
     * The health-check response is cached to protect the database from
     * DoS-style polling. A maximum of 100 entries prevents unbounded memory
     * growth. Cache statistics are recorded for monitoring.
     *
     * @return the configured {@link CacheManager}
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(100)
            .recordStats());
        return manager;
    }
}
