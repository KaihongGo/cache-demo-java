# AI Coding Assistant Instructions

This is a production-ready Spring Boot application demonstrating a sophisticated L1+L2 distributed cache architecture with comprehensive resilience patterns.

## Architecture Overview

**Two-tier caching**: L1 (Caffeine in-memory) → L2 (Redis) → Database
- **Read path**: L1 hit → L2 hit → DB load with breakdown protection
- **Write path**: DB-first → cache invalidation → delayed double delete
- **Cross-node sync**: Redis pub/sub for L1 evictions across nodes

## Key Patterns

### Cache Envelope Pattern
All cached values are wrapped in `CachedValue<T>` records with logical expiration:
```java
CachedValue<User> envelope = new CachedValue<>(data, logicalExpireAtMs);
```
This enables "stale serve + async rebuild" for zero-downtime cache refreshes.

### Multi-layer Defense Strategy
1. **Penetration protection**: Bloom filter pre-check before DB queries
2. **Breakdown protection**: Distributed locks for single-flight loading  
3. **Avalanche prevention**: Staggered TTLs with randomization
4. **Logical expiration**: Serve stale data while rebuilding cache

### Consistency Model
- **DB-first writes**: Always update database before cache operations
- **Delayed double delete**: Immediate + 300ms delayed cache deletion
- **Cross-node invalidation**: Redis pub/sub broadcasts L1 evictions

## Critical Components

- `UserCacheService`: Core cache orchestration with all resilience patterns
- `CachedValue<T>`: Envelope with logical expiration timestamps
- `DistributedLock`: Redis-based single-flight pattern implementation
- `CacheConfig`: Caffeine L1 cache with metrics registration
- `BloomConfig`: Guava bloom filter for penetration defense

## Development Workflow

### Local Setup
```bash
docker-compose up -d    # Start Redis + PostgreSQL
mvn spring-boot:run     # Start application on port 18080
./test-cache.sh        # Run cache behavior tests
```

### Monitoring & Debugging
- **Metrics endpoint**: `http://localhost:18080/actuator/metrics`
- **Cache metrics**: `cache.gets`, `cache.size`, `cache.evictions`
- **Debug logging**: Set `com.example.cache: DEBUG` in application.yml

### Testing Cache Behavior
Use `test-cache.sh` to verify:
- L1/L2 cache hits and misses
- Cache invalidation on updates  
- Bloom filter null object caching
- Cross-node sync (run multiple instances)

## Implementation Guidelines

### Adding New Cached Entities
1. Implement `CacheService<T>` interface
2. Follow `UserCacheService` patterns for consistency
3. Use `CacheKeys` utility for key generation
4. Register cache metrics in configuration
5. Add bloom filter if penetration risk exists

### Configuration Tuning
- **L1 cache size**: Adjust `maximumSize` in `CacheConfig`
- **TTL staggering**: Modify randomization in `writeCache()`  
- **Lock timeouts**: Tune in `DistributedLock` calls
- **Redis pool**: Scale `max-active` for high load

### Production Considerations
- Use Redis Cluster/Sentinel for L2 high availability
- Monitor cache hit rates and adjust sizing accordingly
- Refresh bloom filters periodically to maintain accuracy
- Consider hot key splitting if individual keys become bottlenecks