package com.raghunath.hotelview.config;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling // Required for the timer to work
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("menuCache", "completedOrders");
    }

    // This is the "Cleanup Crew"
    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    public void clearMenuCache() {
        // This just empties the RAM so the next user gets fresh data from the DB
        Cache cache = cacheManager().getCache("menuCache");
        if (cache != null) {
            cache.clear();
        }
    }
}
