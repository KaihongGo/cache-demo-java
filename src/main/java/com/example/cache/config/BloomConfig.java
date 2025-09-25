package com.example.cache.config;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BloomConfig {
    
    @Bean
    public BloomFilter<Long> userBloom() {
        // capacity and fpp tuned; refresh periodically via batch load
        return BloomFilter.create(Funnels.longFunnel(), 10_000_000, 0.01);
    }
}