# Production-Ready Distributed Cache in Spring Boot

A resilient L1+L2 caching solution with explicit consistency controls, breakdown protection, penetration defenses, and comprehensive observability.

## Architecture

- **L1 Cache**: In-memory Caffeine cache for ultra-low latency hot keys
- **L2 Cache**: Redis for shared, scalable cache across nodes  
- **Consistency**: DB-first updates followed by cache invalidations
- **Cross-node sync**: Redis pub/sub for L1 evictions across nodes
- **Protection**: Breakdown prevention, penetration defense, logical expiration
- **Observability**: Prometheus metrics via Micrometer and Actuator

## Features

- **Multi-level caching**: L1 (Caffeine) → L2 (Redis) → Database
- **Penetration protection**: Bloom filter to avoid unnecessary DB queries
- **Breakdown protection**: Distributed locks for single-flight loading
- **Logical expiration**: Serve stale data while rebuilding cache
- **Delayed double delete**: Reduces inconsistency windows
- **Staggered TTLs**: Prevents cache avalanche
- **Cross-node invalidation**: Redis pub/sub for L1 cache eviction
- **Comprehensive metrics**: Cache hit rates, latencies, and error rates

## Prerequisites

- Java 17+
- Maven 3.6+
- Docker & Docker Compose (for Redis and PostgreSQL)

## Quick Start

1. **Start dependencies** (Redis and PostgreSQL):
   ```bash
   docker-compose up -d
   ```

2. **Run the application**:
   ```bash
   mvn spring-boot:run
   ```

3. **Test the API**:
   ```bash
   # Get user (will load from DB and cache)
   curl http://localhost:18080/users/1
   
   # Update user (DB-first, then cache invalidation)
   curl -X PUT http://localhost:18080/users/1 \
     -H "Content-Type: application/json" \
     -d '{"name":"Updated Name","email":"updated@example.com"}'
   
   # Delete user
   curl -X DELETE http://localhost:18080/users/1
   ```

## Monitoring

- **Health**: http://localhost:18080/actuator/health
- **Metrics**: http://localhost:18080/actuator/metrics
- **Prometheus**: http://localhost:18080/actuator/prometheus

### Key Metrics

- `cache_gets_total{cache="l1-cache",result="hit"}` - L1 cache hits
- `cache_gets_total{cache="l1-cache",result="miss"}` - L1 cache misses
- `cache_size{cache="l1-cache"}` - L1 cache size
- `cache_evictions_total{cache="l1-cache"}` - L1 cache evictions

## Configuration

Key settings in `application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000
      lettuce:
        pool:
          max-active: 64
```

## Architecture Decisions

### Cache Strategy
- **Read Path**: L1 → L2 → DB with logical expiration
- **Write Path**: DB-first → Cache invalidation → Delayed double delete
- **Null Object Caching**: Prevents penetration attacks
- **Bloom Filter**: Pre-check before DB queries

### Consistency Model
- **Eventually consistent** between cache layers
- **Strong consistency** between DB and application state
- **Cross-node invalidation** via Redis pub/sub

### Failure Handling
- **Breakdown protection**: Single-flight loading with distributed locks
- **Penetration defense**: Bloom filter + null object caching
- **Avalanche prevention**: Staggered TTLs + logical expiration
- **Graceful degradation**: Serve stale data when needed

## Production Considerations

1. **Redis Setup**: Use Redis Cluster or Sentinel for high availability
2. **Monitoring**: Set up alerts for cache hit rates, error rates
3. **Bloom Filter**: Refresh periodically to maintain accuracy
4. **Hot Keys**: Monitor and potentially split if needed
5. **Memory**: Tune L1 cache size based on application needs
6. **Network**: Consider Redis compression for large values

## Testing Load

Use a load testing tool like Apache Bench or wrk:

```bash
# Test read performance
ab -n 10000 -c 100 http://localhost:18080/users/1

# Monitor cache metrics during load
curl http://localhost:18080/actuator/metrics/cache.gets
```