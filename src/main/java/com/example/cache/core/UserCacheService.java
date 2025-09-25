package com.example.cache.core;

import com.example.cache.config.CacheConfig;
import com.example.cache.model.User;
import com.example.cache.repo.UserRepository;
import com.google.common.hash.BloomFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.example.cache.core.CacheKeys.*;

@Service
public class UserCacheService implements CacheService<User> {
    
    private static final Logger log = LoggerFactory.getLogger(UserCacheService.class);

    private final Cache l1;
    private final RedisTemplate<String, Object> redis;
    private final UserRepository repo;
    private final DistributedLock lock;
    private final BloomFilter<Long> bloom;

    public UserCacheService(CacheManager cacheManager,
                            RedisTemplate<String, Object> redis,
                            UserRepository repo,
                            DistributedLock lock,
                            BloomFilter<Long> bloom) {
        this.l1 = cacheManager.getCache(CacheConfig.L1_CACHE);
        this.redis = redis;
        this.repo = repo;
        this.lock = lock;
        this.bloom = bloom;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<User> getById(long id) {
        String key = userKey(id);
        log.debug("Cache lookup for user ID: {}", id);

        // 1) L1 hit
        CachedValue<User> l1Val = l1.get(key, CachedValue.class);
        if (l1Val != null && !l1Val.isExpired()) {
            log.debug("L1 cache hit for user ID: {}", id);
            return Optional.ofNullable(l1Val.data());
        }

        // 2) L2 hit
        CachedValue<User> l2Val = (CachedValue<User>) redis.opsForValue().get(key);
        if (l2Val != null) {
            log.debug("L2 cache hit for user ID: {}", id);
            // backfill L1 with short TTL
            l1.put(key, l2Val);
            if (!l2Val.isExpired()) {
                return Optional.ofNullable(l2Val.data());
            }

            // logical expiration: stale serve + async rebuild
            String token = lock.tryLock("rebuild:" + key, Duration.ofSeconds(10));
            if (token != null) {
                log.debug("Async rebuild triggered for user ID: {}", id);
                new Thread(() -> {
                    try {
                        repo.findById(id).ifPresentOrElse(
                                fresh -> writeCache(id, fresh),
                                () -> writeNullObject(id)
                        );
                    } finally {
                        lock.unlock("rebuild:" + key, token);
                    }
                }).start();
            }
            return Optional.ofNullable(l2Val.data()); // return slightly stale
        }

        // 3) Penetration defense: Bloom check
        if (!bloom.mightContain(id)) {
            log.debug("Bloom filter miss for user ID: {}, caching null object", id);
            // cache null with short TTL
            writeNullObject(id);
            return Optional.empty();
        }

        // 4) Breakdown protection: single-flight with lock
        String token = lock.tryLock("load:" + key, Duration.ofSeconds(5));
        if (token != null) {
            log.debug("Loading from DB for user ID: {}", id);
            try {
                Optional<User> fromDb = repo.findById(id);
                if (fromDb.isPresent()) {
                    writeCache(id, fromDb.get());
                    return fromDb;
                } else {
                    writeNullObject(id);
                    return Optional.empty();
                }
            } finally {
                lock.unlock("load:" + key, token);
            }
        } else {
            // brief backoff then re-check cache
            try { 
                TimeUnit.MILLISECONDS.sleep(50); 
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            @SuppressWarnings("unchecked")
            CachedValue<User> retry = (CachedValue<User>) redis.opsForValue().get(key);
            if (retry != null) {
                l1.put(key, retry);
                return Optional.ofNullable(retry.data());
            }
            log.warn("Failed to acquire lock and cache miss for user ID: {}", id);
            return Optional.empty();
        }
    }

    @Override
    public void update(long id, User newValue) {
        log.debug("Updating user ID: {}", id);
        // 1) Write DB first
        repo.update(newValue);

        // 2) Delayed double delete: immediate L2+L1 delete
        String key = userKey(id);
        redis.delete(key);
        l1.evict(key);

        // 3) Broadcast L1 invalidation
        redis.convertAndSend(invalidateChannel(), key);

        // 4) Second delayed delete to shrink window
        new Thread(() -> {
            try { 
                TimeUnit.MILLISECONDS.sleep(300); 
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            redis.delete(key);
            l1.evict(key);
            redis.convertAndSend(invalidateChannel(), key);
        }).start();
    }

    @Override
    public void delete(long id) {
        log.debug("Deleting user ID: {}", id);
        // 1) Delete DB
        repo.delete(id);

        // 2) Delete L2+L1 and broadcast
        String key = userKey(id);
        redis.delete(key);
        l1.evict(key);
        redis.convertAndSend(invalidateChannel(), key);

        // 3) Delayed second delete
        new Thread(() -> {
            try { 
                TimeUnit.MILLISECONDS.sleep(300); 
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            redis.delete(key);
            l1.evict(key);
            redis.convertAndSend(invalidateChannel(), key);
        }).start();
    }

    private void writeCache(long id, User data) {
        long logicalTtlMs = TimeUnit.MINUTES.toMillis(5) + 
                           (long)(Math.random() * TimeUnit.MINUTES.toMillis(5)); // stagger TTL
        CachedValue<User> envelope = new CachedValue<>(data, System.currentTimeMillis() + logicalTtlMs);
        String key = userKey(id);
        redis.opsForValue().set(key, envelope, Duration.ofMinutes(10)); // physical TTL longer than logical
        l1.put(key, envelope);
        bloom.put(id); // keep BF in sync on writes
        log.debug("Cached user ID: {}", id);
    }

    private void writeNullObject(long id) {
        String keyNull = userNullKey(id);
        redis.opsForValue().set(keyNull, "NULL", Duration.ofSeconds(30));
        // Optionally also place a marker in L1
        CachedValue<User> nullEnvelope = new CachedValue<>(null, 
                                                          System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30));
        l1.put(keyNull, nullEnvelope);
        log.debug("Cached null object for user ID: {}", id);
    }
}