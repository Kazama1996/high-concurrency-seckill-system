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

# Run only unit tests (fast, no containers)
./gradlew test -DincludeTags=unit

# Run only integration tests (Testcontainers: Postgres / Kafka / Redis)
./gradlew test -DincludeTags=integration

# Run a single test class
./gradlew test --tests "com.kazama.redis_cache_demo.RedisCacheDemoApplicationTests"

# Clean build
./gradlew clean build
```

Every test is tagged `@Tag("unit")` or `@Tag("integration")`. `build.gradle`'s `useJUnitPlatform` block reads the `-DincludeTags` / `-DexcludeTags` system properties (comma-separated) and filters on them — this is the mechanism CI uses to run unit and integration tests as separate jobs (see CI/CD below).

Integration tests extend one of three Testcontainers-backed base classes, chosen by what infra the test actually needs rather than always paying for the full stack:

| Base class | Containers | Use for |
|---|---|---|
| `AbstractIntegrationTest` | Postgres + Kafka + Redis, `@SpringBootTest` | End-to-end flows spanning multiple infra pieces |
| `AbstractRedisIntegrationTest` | Redis only | Redis/Lua-only tests — independent of the other two, no Spring context |
| `AbstractPostgresIntegrationTest` | Postgres only, `@DataJpaTest` | Repository/native-query tests; the Postgres container is a JVM-wide singleton started in a static block (not a JUnit `@Container`), since each subclass gets its own `@DataJpaTest` context and a `@Container`-managed instance would otherwise start a second Postgres per subclass |

---

## CI/CD

`.github/workflows/ci.yml` runs on push and PR to `main`, with three jobs:

| Job | Runs | `needs` |
|---|---|---|
| `unit-tests` | `./gradlew test -DincludeTags=unit` | — |
| `integration-tests` | `./gradlew test -DincludeTags=integration` | — |
| `build` | `./gradlew build -x test`, uploads the jar artifact | `unit-tests`, `integration-tests` |

`unit-tests` and `integration-tests` run in parallel (no `needs:` between them — they use disjoint `-DincludeTags` filters and don't share fixtures); `build` waits on both. Use these exact job names when configuring required status checks for branch protection.

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
│   ├── service/           # OrderService (@Transactional: save order + outbox record),
│   │                      # OutboxStatusUpdateService (status transitions), OutboxPublisherService (Kafka send + status update)
│   ├── entity/            # Orders, OrderCreatedOutbox
│   └── repository/        # OrderRepository, OrderCreatedOutboxRepository
├── product/
│   ├── controller/        # ProductController
│   ├── service/           # ProductService (lock-protected cache), ProductCacheService (Redis R/W)
│   ├── entity/            # Product JPA entity
│   └── repository/
├── notification/
│   └── kafka/consumer/    # SeckillOrderNotificationConsumer (manual-ack, idempotency-checked mock email log)
└── infra/
    ├── bloomfilter/        # Redisson Bloom Filter (cache penetration guard)
    ├── cache/              # CacheResult<T> wrapper (HIT / MISS / NULL_HIT)
    ├── circuitbreaker/     # @ProductDBCircuitBreaker, @RedisCircuitBreaker, etc. (annotations)
    ├── idempotency/        # NotificationIdempotencyService (Redis-backed dedup for the notification consumer)
    ├── lock/               # DistributedLockService (Redisson RLock with watchdog)
    ├── outbox/
    │   ├── config/         # OutboxQuartzConfig (JobDetail/Trigger for outbox polling)
    │   └── enums/          # OutboxStatus (PENDING, SENDING, SENT, FAILED, DEAD_LETTER)
    ├── ratelimit/          # @RateLimit AOP + sliding window Lua script
    ├── schedule/
    │   ├── config/         # QuartzConfig (JobFactory/SchedulerFactoryBean/Scheduler — pure infra),
    │   │                   # AutowiringSpringBeanJobFactory
    │   └── job/            # SeckillCacheWarmingJob (Quartz), OutboxPollingJob (Quartz, every 5s — dispatch + stuck-SENDING recovery)
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
  │               └─ Bloom filter check → Redisson RLock (3s wait, watchdog-renewed lease)
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
  ├─ SELECT order_created_outbox WHERE status IN (PENDING, FAILED), top 500 by createdAt
  ├─ bulk UPDATE status=SENDING for that batch (claims the rows before dispatch)
  ├─ KafkaTemplate.send(topic, payload) per record
  │   ├─ success → status=SENT
  │   └─ failure → retry_count++; status=DEAD_LETTER if retry_count ≥ MAX_RETRY_ATTEMPTS(5), else status=FAILED (retried next cycle)
  └─ separately: SELECT order_created_outbox WHERE status=SENDING AND updatedAt < now-5min
      └─ found (e.g. app crashed mid-publish) → force straight to status=DEAD_LETTER

  [Kafka Consumer: SeckillOrderNotificationConsumer, manual ack]
  ├─ NotificationIdempotencyService.isAlreadyProcessed(orderId)?
  │   └─ yes → ack + skip (duplicate delivery)
  └─ no → log mock notification email → markProcessed(orderId) → ack
      (unhandled exception → not acked → container's DefaultErrorHandler retries up to 4x, then routes to the topic's .DLT)
```

---

## Redis Key Schema

| Key Pattern | Type | TTL | Purpose |
|---|---|---|---|
| `seckill:activity:{activityId}` | String (JSON) | Until activity ends | Cached SeckillActivityDTO |
| `seckill:stock:{activityId}` | String (integer) | Until activity ends | Available stock counter |
| `seckill:orders:{activityId}` | Set | Until activity ends | Set of winning userIds (idempotency) |
| `seckill:activity:rewarm:{activityId}` | Lock | 30s lease, watchdog-renewed | Distributed lock for cache rewarming |
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
Order creation and outbox record insertion share a single `@Transactional` boundary in `OrderService`. Quartz polls every 5 seconds for `PENDING` and `FAILED` records and publishes to Kafka — no dual-write risk.

`OutboxStatus` is a 5-state machine: `PENDING` → `SENDING` → `SENT`, or `SENDING` → `FAILED` (retryable) → ... → `DEAD_LETTER` (terminal, once `OutboxStatusUpdateService.MAX_RETRY_ATTEMPTS` = 5 attempts have failed). `OutboxPollingJob` claims a batch by bulk-updating the matched rows to `SENDING` *before* dispatch, so a later poll cycle can't re-claim and double-publish the same rows, then calls `OutboxPublisherService.publish` per record. On send failure, `OrderCreatedOutboxRepository.bulkMarkFailed` runs a native `UPDATE ... SET retry_count = retry_count+1, status = CASE WHEN retry_count+1 >= :maxRetry THEN 'DEAD_LETTER' ELSE 'FAILED' END` — the retry-count increment and the terminal-state boundary check happen atomically in one SQL statement.

Each poll cycle also looks for records stuck in `SENDING` for longer than `OutboxPollingJob.SENDING_TIMEOUT` (5 minutes) — e.g. the app crashed after claiming a batch but before the Kafka send callback ran — and force-fails them straight to `DEAD_LETTER` via `markFailedBatch(ids, MAX_RETRY_ATTEMPTS)`, since a stuck record's actual delivery outcome is unknown and shouldn't be silently retried forever.

Quartz infrastructure (`JobFactory`, `SchedulerFactoryBean`, `Scheduler`) lives in `infra/schedule/config/QuartzConfig`, kept free of any job-specific definitions. Each feature owns its own `JobDetail`/`Trigger` beans in its own `config` package (e.g. `infra/outbox/config/OutboxQuartzConfig`), following this repo's package-by-feature convention — mirrors how `order/config`, `product/config`, `seckill/config` each hold their own `CircuitBreakerConfig`. `schedulerFactoryBean` accepts `JobDetail[]`/`Trigger[]` so new jobs are auto-collected without modifying that bean method.

### Consumer-Side Idempotency
`SeckillOrderNotificationConsumer` uses manual acknowledgment (`ContainerProperties.AckMode.MANUAL_IMMEDIATE`, configured in `infra/config/KafkaConsumerConfig`) instead of auto-ack, so a record is only committed once it has actually been handled. Before processing, it checks `NotificationIdempotencyService.isAlreadyProcessed(orderId)` — a Redis key (`seckill:notification:processed:{orderId}`, 24h TTL) — to guard against redelivery, since Kafka's at-least-once semantics plus the container's own retry can otherwise redeliver the same record. On success it calls `markProcessed(orderId)` *after* the (mock) send, then acks; an unhandled exception during processing propagates instead of acking, so the container's `DefaultErrorHandler` retries (up to 4x with backoff) and then routes the record to its `.DLT` topic via `DeadLetterPublishingRecoverer` rather than dropping it silently.

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

### Claude Code Execution Boundaries (MUST follow — takes precedence over the workflow below)

**Standard workflow:**
1. Make the code changes
2. **Stop here** — do not auto-commit. List which files were changed and what was changed, then wait for explicit user confirmation (e.g. the user says "OK", "looks good", "go ahead and commit")
3. Only after receiving explicit confirmation, write the commit message per the convention below and run `git commit`
4. After committing, **stop again** — report the commit message content, and do not take any further action

**Strictly forbidden** (even mid-workflow, even if the user gives a vague instruction like "handle it", "finish this up", "continue" — unless the user explicitly names the action):
- `git push` (no branch is exempt, including feature branches)
- `gh pr create` or creating a PR/MR in any form
- Running `git commit` without explicit user confirmation

**Exception:** Only run `git push` or open a PR when the user explicitly says so (e.g. "push it" / "open a PR for me"). If it's unclear whether consent was explicit enough, ask first — never assume.

> Steps 4 and 5 in the Flow section below (push, open PR, merge) are always performed **manually by the user**. Claude Code does not perform them proactively, and only helps assemble the relevant commands when explicitly asked.


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

---

## Agent skills

### Issue tracker

Issues live in GitHub Issues (Kazama1996/high-concurrency-seckill-system), via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.