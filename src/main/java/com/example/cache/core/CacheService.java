package com.example.cache.core;

import java.util.Optional;

public interface CacheService<T> {
    Optional<T> getById(long id);
    void update(long id, T newValue);
    void delete(long id);
}