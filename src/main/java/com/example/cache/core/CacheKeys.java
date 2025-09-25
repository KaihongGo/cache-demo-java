package com.example.cache.core;

public final class CacheKeys {
    private CacheKeys() {}

    // Use namespaces to avoid collisions and enable selective eviction
    public static String userKey(long userId) {
        return "user:v1:" + userId;
    }

    public static String userNullKey(long userId) { // for null-object caching
        return "user:null:v1:" + userId;
    }

    public static String invalidateChannel() {
        return "cache:invalidate";
    }
}