package com.raghunath.hotelview.config;

import com.github.ben_manes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Register the exact cache names explicitly in Java code
        cacheManager.setCacheNames(Arrays.asList("menuCache", "menuSummaryCache"));

        // Apply your Render 1-hour expiration, 10-hotel limit rules directly
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterAccess(1, TimeUnit.HOURS));

        return cacheManager;
    }
}