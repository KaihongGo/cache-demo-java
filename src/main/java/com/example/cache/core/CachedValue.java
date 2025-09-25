package com.example.cache.core;

public record CachedValue<T>(T data, long logicalExpireAtEpochMs) {
    public boolean isExpired() { 
        return System.currentTimeMillis() >= logicalExpireAtEpochMs; 
    }
}