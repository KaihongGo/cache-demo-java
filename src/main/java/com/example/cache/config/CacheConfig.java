package com.example.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {

    public static final String L1_CACHE = "l1-cache";

    @Bean
    public Caffeine<Object, Object> caffeineSpec() {
        return Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(Duration.ofMinutes(5))   // short TTL to reduce staleness
                .recordStats();
    }

    @Bean
    public SimpleCacheManager cacheManager(Caffeine<Object, Object> caffeine, MeterRegistry meterRegistry) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeine.build();
        CaffeineCache l1 = new CaffeineCache(L1_CACHE, nativeCache);
        
        // Register metrics
        CaffeineCacheMetrics.monitor(meterRegistry, nativeCache, L1_CACHE);
        
        SimpleCacheManager mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(l1));
        return mgr;
    }
}