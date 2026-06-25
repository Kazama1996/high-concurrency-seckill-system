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

Env config is split into three files, by which process reads them:

| File | Read by | Hostnames |
|---|---|---|
| `.env.infra` | `docker compose` (via `make`), for postgres/redis/kafka/pgadmin container env | — |
| `.env.container` | the `app` service in `docker-compose.yml`, when running the app in a container | container hostnames (`postgres`, `redis`, `kafka`) |
| `.env.local` | IntelliJ EnvFile plugin, when running the app as a local JVM process | `localhost` |

Each has a corresponding `.example` template checked into git. Copy and fill in credentials:
```bash
cp .env.infra.example .env.infra
cp .env.container.example .env.container
cp .env.local.example .env.local
```

Key variables (see each `.example` file for the full list):

| Variable | Description |
|---|---|
| `DB_USERNAME` / `DB_PASSWORD` (`.env.container` / `.env.local`), `POSTGRES_USER` / `POSTGRES_PASSWORD` (`.env.infra`) | PostgreSQL credentials |
| `REDIS_PASSWORD` | Redis auth password |
| `PGADMIN_EMAIL` / `PGADMIN_PASSWORD` | pgAdmin UI login (`.env.infra` only) |
| `JPA_DDL_AUTO` | Set to `create` on first run, then `none` |
| `SECKILL_WARMUP_MINUTES_BEFORE` | Minutes before seckill to warm cache (default: `60`, use `1` for local testing) |

### IntelliJ setup for local development

1. Install the **EnvFile** plugin.
2. In the app's Run Configuration, enable EnvFile and point it at `.env.local` — not `.env.infra` or `.env.container`.
3. `.env.local` must use `localhost` hostnames (`DB_HOST`, `REDIS_HOST`, `KAFKA_BOOTSTRAP_SERVERS=localhost:9094`), since the app runs as a local JVM process outside the Docker network, not as a container on `app-network`.

---

## Running the Project

`docker-compose.app.yml` was merged into `docker-compose.yml`: the `app` service is tagged `profiles: ["app"]`, so infra-only commands omit it by default, and `depends_on`/healthcheck ordering applies across infra and app in one file. A `Makefile` wraps the common commands so the correct `--env-file` flag is always applied — prefer `make` targets over raw `docker compose` invocations.

### Full stack (infra + app)

```bash
make app-up
```

App is available at `http://localhost:8080`.

### Infra only + local Spring Boot via IntelliJ (recommended for development)

```bash
make infra-up
```

Then run the app via IntelliJ with the Run Configuration's EnvFile plugin pointed at `.env.local` (see IntelliJ setup above), or:
```bash
./gradlew bootRun
```
(`./gradlew bootRun` requires `.env.local` variables to be exported into the shell environment first, since Gradle does not read `.env.local` itself.)

### Other Makefile targets

| Command | Effect |
|---|---|
| `make infra-up` | Start infra only (postgres / redis / kafka / pgadmin / kafka-ui) |
| `make infra-down` | Stop infra only |
| `make app-up` | Start app (and infra, if not already running) |
| `make app-down` | Stop the app container only, leave infra running |
| `make down-all` | Stop everything (infra + app) |
| `make restart-app` | Rebuild and restart the app only, after code changes |
| `make logs` | Tail app logs |
| `make logs-infra s=postgres` | Tail logs for a specific infra service |
| `make ps` | Show status of all containers |
| `make clean` | Stop everything and remove volumes (wipes DB data) |

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
    ├── outbox/
    │   ├── config/         # OutboxQuartzConfig (JobDetail/Trigger for outbox polling)
    │   ├── entity/         # Outbox
    │   ├── enums/          # OutboxStatus
    │   └── repository/     # OutboxRepository
    ├── ratelimit/          # @RateLimit AOP + sliding window Lua script
    ├── schedule/
    │   ├── config/         # QuartzConfig (JobFactory/SchedulerFactoryBean/Scheduler — pure infra),
    │   │                   # AutowiringSpringBeanJobFactory
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

Quartz infrastructure (`JobFactory`, `SchedulerFactoryBean`, `Scheduler`) lives in `infra/schedule/config/QuartzConfig`, kept free of any job-specific definitions. Each feature owns its own `JobDetail`/`Trigger` beans in its own `config` package (e.g. `infra/outbox/config/OutboxQuartzConfig`), following this repo's package-by-feature convention — mirrors how `order/config`, `product/config`, `seckill/config` each hold their own `CircuitBreakerConfig`. `schedulerFactoryBean` accepts `JobDetail[]`/`Trigger[]` so new jobs are auto-collected without modifying that bean method.

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
make infra-up

# 2. Run app (local profile, via IntelliJ EnvFile -> .env.local, or:)
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

1. Sync and branch off `main`:
```bash
   git checkout main
   git fetch origin
   git reset --hard origin/main
   git checkout -b feature/your-feature
```
2. Commit with conventional commit messages
3. Before pushing, sync with latest `main` and rebase:
```bash
   git checkout main
   git fetch origin
   git reset --hard origin/main
   git checkout feature/your-feature
   git rebase main
```
> If conflicts occur during rebase, stop and ask the user how to resolve them — do not resolve automatically.
> `main` is never modified directly — it is always force-aligned to `origin/main` via `fetch` + `reset --hard`, never `pull`, since `main` represents the team's agreed-upon stable baseline and should never carry local merge commits or stray changes.
4. Push and open a PR into `main`:
```bash
   git push origin feature/your-feature
```
5. Merge via GitHub PR (squash or merge commit — both used in this repo)