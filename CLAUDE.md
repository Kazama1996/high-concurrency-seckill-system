# redis-cache-demo

A production-grade flash sale (seckill) system demonstrating high-concurrency patterns with Redis: atomic Lua-based stock deduction, distributed locking, cache protection strategies (penetration, breakdown, avalanche), per-domain circuit breakers, and transactional event publishing via the Outbox pattern.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.10 |
| ORM | Spring Data JPA / Hibernate 6 |
| Cache / Lock | Redis 7 via Redisson (RLock, Bloom Filter) |
| Messaging | Apache Kafka 3.7.0 |
| Scheduling | Quartz (Spring Boot Starter) |
| Resilience | Resilience4j 2.2.0 (CircuitBreaker, Retry) |
| Database | PostgreSQL 15 |
| Build | Gradle (Wrapper) |
| ID Generation | Snowflake (`com.github.Kazama1996:common-spring-boot-starter:v1.0.0`) |

---

## Environment Setup

1. Copy the example env file and fill in credentials:
   ```bash
   cp .env.example .env
   ```

2. Required variables (see `.env.example` for all defaults):

   | Variable | Description |
   |---|---|
   | `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL credentials |
   | `REDIS_PASSWORD` | Redis auth password |
   | `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` | pgAdmin UI login (Docker only) |
   | `JPA_DDL_AUTO` | Set to `create` on first run, then `none` |
   | `SECKILL_WARMUP_MINUTES_BEFORE` | Minutes before seckill to warm cache (default: `60`, use `1` for local testing) |

---

## Running the Project

The compose setup is split into two files:
- `docker-compose.yml` — infrastructure only (PostgreSQL, Redis, Kafka, Kafka-UI, pgAdmin)
- `docker-compose.app.yml` — Spring Boot app only (joins the infra network as external)

### Full stack (infra + app)

```bash
docker compose up -d && docker compose -f docker-compose.app.yml up --build
```

App is available at `http://localhost:8080`.

### Infra only + local Spring Boot (recommended for development)

```bash
# Start infrastructure
docker compose up -d

# Run the app locally (local profile enables dev/diagnostic endpoints)
./gradlew bootRun
```

### App container only (infra already running)

```bash
docker compose -f docker-compose.app.yml up --build
```

> `docker-compose.app.yml` expects the network `redis-cache-demo_app-network` to exist,
> which is created automatically when `docker compose up` starts the infra stack.

---

## Build & Test Commands

```bash
# Build (skip tests)
./gradlew build -x test

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.kazama.redis_cache_demo.RedisCacheDemoApplicationTests"

# Clean build
./gradlew clean build
```

---

## Service Ports

| Service | Port | URL |
|---|---|---|
| Spring Boot app | 8080 | `http://localhost:8080` |
| PostgreSQL | 5432 | — |
| Redis | 6379 | — |
| Kafka (external) | 9094 | — |
| Kafka-UI | 8090 | `http://localhost:8090` |
| pgAdmin | 5050 | `http://localhost:5050` |

---

## API Reference

### Seckill (`SeckillController`)
All profiles. Rate-limited per user+activity (5 req / 60 s). Redis circuit breaker applied.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/seckill/deduct` | Deduct stock and create order |

Request body:
```json
{ "activityId": 1, "userId": 42, "quantity": 1 }
```
Returns: `orderId` (Long)

### Product (`ProductController`)
All profiles.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/products/{id}` | Get product by ID (cache-aside) |

### Seckill Activity Management (`SeckillActivityController`)
`local` / `dev` profiles only.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/seckill/activities` | Create one or more seckill activities |

### Data Init (`DataInitController`)
`local` / `dev` profiles only.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/dev/init/products/default` | Seed 10 default products |
| `POST` | `/api/v1/dev/init/products?total=N` | Seed N products |
| `DELETE` | `/api/v1/dev/init/seckill/{activityId}/reset` | Reset a seckill activity for re-testing |

### Diagnostic (`DiagnosticController`)
`local` / `dev` profiles only.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/dev/diagnostic/seckill/{activityId}` | Compare Redis stock vs DB remaining stock |

---

## Source Layout

```
src/main/java/com/kazama/redis_cache_demo/
├── seckill/
│   ├── controller/        # SeckillController, SeckillActivityController
│   ├── service/           # SeckillService (orchestration), SeckillActivityService (DB+lock),
│   │                      # SeckillActivityCacheService (Redis R/W + Lua)
│   ├── entity/            # SeckillActivity JPA entity
│   ├── dto/               # Request/response DTOs
│   ├── event/             # SeckillActivityEventListener (schedules cache warming after creation)
│   └── repository/
├── order/
│   ├── service/           # OrderService (@Transactional: save order + outbox record)
│   ├── entity/            # Orders, OrderCreatedOutbox
│   └── repository/
├── product/
│   ├── controller/        # ProductController
│   ├── service/           # ProductService (lock-protected cache), ProductCacheService (Redis R/W)
│   ├── entity/            # Product JPA entity
│   └── repository/
├── notification/
│   └── kafka/consumer/    # SeckillOrderNotificationConsumer (mock email log)
└── infra/
    ├── bloomfilter/        # Redisson Bloom Filter (cache penetration guard)
    ├── cache/              # CacheResult<T> wrapper (HIT / MISS / NULL_HIT)
    ├── circuitbreaker/     # @ProductDBCircuitBreaker, @RedisCircuitBreaker, etc. (annotations)
    ├── lock/               # DistributedLockService (Redisson RLock with watchdog)
    ├── ratelimit/          # @RateLimit AOP + sliding window Lua script
    ├── schedule/
    │   └── job/            # SeckillCacheWarmingJob (Quartz), OutboxPollingJob (Quartz, every 5s)
    ├── diagnostic/         # DiagnosticController + DiagnosticService
    └── init/               # DataInitController + DataInitService (seed data)

src/main/resources/
├── application.yml         # All config (datasource, Redis, Kafka, Resilience4j, Quartz)
├── lua/
│   ├── seckill/deduct_stock.lua           # Atomic stock deduction + idempotency
│   └── ratelimit/sliding_window_ratelimiter.lua  # Sorted-set sliding window
└── db/                     # SQL schema files (auto-run on startup via spring.sql.init)
```

---

## Seckill Flow

```
POST /api/v1/seckill/deduct
  │
  ├─ @RateLimit (Lua sliding window, key: seckill:user:{uid}:activity:{aid}, 5 req/60s)
  ├─ @RedisCircuitBreaker
  │
  ▼ SeckillService.deductStock()
  │
  ├─ 1. SeckillActivityCacheService.getActivity(activityId)
  │       Redis GET seckill:activity:{id}
  │       MISS → SeckillActivityService.rewarming()
  │               └─ Bloom filter check → Redisson RLock (3s wait / 10s lease)
  │                   └─ Double-check cache → load from DB → write back to Redis
  │       NULL_HIT → throw 404
  │
  ├─ 2. Validate quantity (1 ≤ qty ≤ maxQuantityPerOrder)
  │
  ├─ 3. Validate time window (startTime ≤ now ≤ endTime)
  │
  ├─ 4. SeckillActivityCacheService.deductStock() → deduct_stock.lua
  │       KEYS: seckill:stock:{id}, seckill:orders:{id}
  │       -1 → activity expired (key gone)
  │       -2 → duplicate order (userId already in orders set)
  │       -3 → stock exhausted
  │       N  → remaining stock (success)
  │
  ├─ 5. orderDBCircuitBreaker.tryAcquirePermission()
  │
  └─ 6. OrderService.createOrder() [@Transactional]
          ├─ INSERT orders
          └─ INSERT order_created_outbox (status=PENDING)

  [Quartz OutboxPollingJob, every 5s]
  └─ SELECT order_created_outbox WHERE status=PENDING
      └─ KafkaTemplate.send(topic, payload)
          ├─ success → status=SENT
          └─ failure → status=FAILED (retried next cycle)

  [Kafka Consumer: SeckillOrderNotificationConsumer]
  └─ Log mock notification email
```

---

## Redis Key Schema

| Key Pattern | Type | TTL | Purpose |
|---|---|---|---|
| `seckill:activity:{activityId}` | String (JSON) | Until activity ends | Cached SeckillActivityDTO |
| `seckill:stock:{activityId}` | String (integer) | Until activity ends | Available stock counter |
| `seckill:orders:{activityId}` | Set | Until activity ends | Set of winning userIds (idempotency) |
| `seckill:activity:rewarm:{activityId}` | Lock | 10s lease | Distributed lock for cache rewarming |
| `product:{productId}` | String (JSON) | 3600s + random [0,300)s | Cached ProductDTO |
| `product:null:{productId}` | String | 120s | Null sentinel (cache penetration guard) |
| `seckill:user:{uid}:activity:{aid}` | ZSet | 60s | Sliding window rate limit entries |

---

## Key Design Patterns

### Atomic Lua over WATCH/MULTI/EXEC
`deduct_stock.lua` executes stock check + idempotency (SADD) + decrement (DECRBY) in a single Redis atomic operation. Avoids retry overhead from optimistic locking under high contention.

### Cache Protection
- **Penetration:** Bloom filter rejects requests for IDs that were never inserted — prevents DB queries for non-existent keys.
- **Breakdown:** Redisson RLock (with watchdog auto-renewal) ensures only one thread reloads a hot key from DB on cache miss.
- **Avalanche:** Product cache TTL = 3600s + random [0, 300)s jitter to stagger mass expiry.

### Outbox Pattern
Order creation and outbox record insertion share a single `@Transactional` boundary in `OrderService`. Quartz polls every 5 seconds for `PENDING` records and publishes to Kafka — no dual-write risk.

### Circuit Breaker Topology (Resilience4j)
Four independent circuit breakers, each with: sliding window size 10, failure threshold 50%, open-state wait 10s, 3 half-open probe calls.

| Breaker | Guards |
|---|---|
| `productDB` | Product DB queries |
| `seckillActivityDB` | Seckill activity DB queries |
| `orderDB` | Order creation (checked via `tryAcquirePermission()`) |
| `redis` | Redis operations (records `RedisException`, `TimeoutException`) |

### Cache Warming
When a seckill activity is created, a `SeckillActivityCreatedEvent` fires after transaction commit. `SeckillActivityEventListener` schedules a Quartz one-shot job to run `SECKILL_WARMUP_MINUTES_BEFORE` minutes before the activity's start time, which pre-populates `seckill:activity:*` and `seckill:stock:*` keys in Redis.

---

## Common Development Workflows

### Typical local test flow

```bash
# 1. Start infrastructure
docker compose up postgres redis kafka -d

# 2. Run app (local profile)
./gradlew bootRun

# 3. Seed products
curl -X POST http://localhost:8080/api/v1/dev/init/products/default

# 4. Create a seckill activity (starts 1 minute from now)
curl -X POST http://localhost:8080/api/v1/seckill/activities \
  -H "Content-Type: application/json" \
  -d '[{"productId":1,"totalStock":100,"maxQuantityPerOrder":1,"seckillPrice":9.9,"startTime":"...","endTime":"..."}]'

# 5. Hit the seckill endpoint
curl -X POST http://localhost:8080/api/v1/seckill/deduct \
  -H "Content-Type: application/json" \
  -d '{"activityId":1,"userId":42,"quantity":1}'

# 6. Verify stock consistency (Redis vs DB)
curl http://localhost:8080/api/v1/dev/diagnostic/seckill/1

# 7. Reset for another load test run
curl -X DELETE http://localhost:8080/api/v1/dev/init/seckill/1/reset
```

### Lua scripts location
- Stock deduction: `src/main/resources/lua/seckill/deduct_stock.lua`
- Rate limiting: `src/main/resources/lua/ratelimit/sliding_window_ratelimiter.lua`

### DB schema
Auto-applied on startup via `spring.sql.init`. SQL files live in `src/main/resources/db/`.
Set `JPA_DDL_AUTO=create` on first run, then switch to `none`.

---

## Git Workflow

### Branch naming

| Prefix | Use for |
|---|---|
| `feature/` | New features (`feature/seckill-deduct-stock-via-lua`) |
| `fix/` | Bug fixes (`fix/lua-stock-deduction-ambiguous-return-code`) |
| `refactor/` | Refactoring without behaviour change (`refactor/circuitbreaker-cleanup-directory`) |
| `docs/` | Documentation only (`docs/readme`) |
| `chore/` | Build, deps, tooling |

### Commit message convention (Conventional Commits)

```
<type>: <short imperative summary>

Optional body explaining why, not what.
```

Types: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`

Examples from this repo:
```
feat: add reset seckill activity API for load testing
fix: resolve ambiguous return code 0 in Lua stock deduction script
refactor: move CircuitBreakerConfig to their respective domain packages
docs: finalize README with load test results and observations
```

### Flow

1. Branch off `main`: `git checkout -b feature/your-feature`
2. Commit with conventional commit messages
3. Rebase onto `main` before pushing: `git rebase main`
4. Open a PR into `main`
5. Merge via GitHub PR (squash or merge commit — both used in this repo)
