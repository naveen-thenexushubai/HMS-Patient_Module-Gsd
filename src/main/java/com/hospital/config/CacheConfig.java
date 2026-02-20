package com.hospital.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine-backed CacheManager for ZIP code lookup caching.
 *
 * 50,000 entry maximum covers all ~43,000 US ZIP codes with room for growth.
 * 24-hour TTL matches ZIP code data stability (ZIP assignments rarely change).
 *
 * @EnableCaching activates Spring's @Cacheable annotation processing.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(24, TimeUnit.HOURS)
        );
        return manager;
    }
}
