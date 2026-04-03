# Seckill System

## Overview

This project is a **high-concurrency flash sale (seckill) system** built to simulate the core backend challenges of time-limited, inventory-scarce product launches — a scenario where thousands of requests compete for a limited number of items within seconds.

The system addresses three fundamental problems in this domain:

- **Overselling** — simultaneous requests decrementing stock below zero
- **Cache stampede** — a sudden spike of requests hitting the database when cached data expires
- **Order consistency** — ensuring that a successful stock deduction always results in a persisted order, even under partial failure

To solve these, the system combines **atomic Redis Lua scripts** for stock deduction, a **synchronous order creation flow with the Outbox Pattern** to guarantee at-least-once delivery via Kafka, and **Resilience4j circuit breakers** to prevent cascading failures under load. Cache protection strategies (cache penetration, breakdown, and avalanche) are applied at the product query layer.

## Architecture

![Architecture](docs/images/architecture.svg)

The system is structured into four layers within a single Spring Boot application:

- **Infra** — shared components applied across all requests: sliding-window rate limiting (Lua), Bloom Filter guards, and Resilience4j circuit breakers
- **Controllers** — user-facing APIs (Seckill, Product) and internal tooling (Diagnostic, Data Init) for load test setup and verification
- **Async Layer** — a Quartz scheduler polls the Outbox table every 5 seconds and relays events to Kafka; a separate consumer handles downstream email notification
- **External Services** — Redis for stock, idempotency, and cache; PostgreSQL for persistence; Kafka Broker as the message transport

## Cache Protection Flow

![Cache Protection Flow](docs/images/getProductById-flow.svg)

The `getProductById` method defends against all three classic cache failure modes through a layered, sequential strategy.

**1. Cache penetration** — A Bloom Filter is checked before any cache or DB access. If the product ID has never existed in the system, the request is rejected immediately without touching Redis or PostgreSQL.

**2. Cache breakdown** — On a cache MISS, a distributed lock (Redisson `RLock`) is acquired before loading from the database. Competing threads that fail to acquire the lock enter an exponential backoff loop with jitter. Before each retry, a cache peek is performed — if another thread has already repopulated the cache, the waiting thread returns early without hitting the DB. Once the lock is acquired, a double-check of Redis is performed to prevent redundant DB reads.

**3. Cache avalanche** — When writing a product back to Redis, a random TTL offset is applied on top of the base expiry, spreading cache expirations across time and preventing a mass simultaneous expiry.

**Null value caching** — If the DB query returns no result, a `NULL_VALUE` sentinel is written to Redis with a short TTL. Subsequent requests for the same non-existent ID are short-circuited at the cache layer, preventing repeated DB queries for phantom keys.

**Circuit breaker** — The DB call is wrapped in a Resilience4j circuit breaker. If the DB is unavailable, the circuit opens and a `ServiceUnavailableException` is thrown immediately, preventing thread exhaustion from pile-up queries.


## Key Design Decisions

### 1. Why Outbox Pattern (not direct Kafka publish)

`@Transactional` only guarantees atomicity within a single resource. Publishing directly to Kafka inside a DB transaction creates a dual-write problem — if the DB commits but Kafka publish fails, the order exists but no notification is ever sent. Conversely, if Kafka publishes but the DB rolls back, a ghost event is emitted.

The Outbox Pattern solves this by writing the event to an `outbox` table in the same DB transaction as the order. A Quartz scheduler polls for `PENDING` records every 5 seconds and relays them to Kafka, marking each as `SENT` on success or `FAILED` for retry on the next cycle. This guarantees at-least-once delivery using only DB atomicity — no distributed transaction required.

> **Known gap**: the current implementation is at-least-once. If the scheduler crashes between a successful publish and marking the record `SENT`, the Consumer may receive a duplicate. Idempotent consumption via an `eventId` + processed-events table is the standard mitigation.

---

### 2. Why Lua script (not WATCH/MULTI/EXEC)

Redis executes Lua scripts atomically on a single thread, making the entire check-and-decrement operation a TOCTOU-free unit. `WATCH/MULTI/EXEC` also provides atomicity, but under high contention it suffers from repeated transaction aborts — any concurrent write to a watched key causes the entire transaction to retry, degrading throughput precisely when it matters most.

The Lua script combines three operations in a single round trip: idempotency check (SADD), stock validation, and decrement. This eliminates retry overhead and keeps the hot path as lean as possible.

---

### 3. Why order creation is synchronous (not async via Kafka)

A seckill success response must guarantee that an order already exists — the user is immediately redirected to a payment page. An async approach (e.g. publishing to Kafka and building the order in a consumer) introduces a window where the user is told they won but no order is ready yet, causing a broken payment flow.

The trade-off is DB throughput dependency. This is acceptable here because the Lua script filters the overwhelming majority of requests at the Redis layer; only the small number of successful stock deductions ever reach the DB, keeping write volume low even under heavy load.

---

### 4. Why per-domain circuit breakers (not a shared one)

A shared circuit breaker creates implicit coupling between unrelated domains — a spike in `product` query failures would trip the breaker and take down `seckill` and `order` flows with it. Separate circuit breakers per domain (seckill, order, product) provide fault isolation: one domain's instability cannot cascade into another.

This is an application of the **Bulkhead Pattern**. It also allows each domain to be tuned independently — failure rate thresholds, slow call thresholds, and wait durations can reflect the actual SLA of each operation rather than a one-size-fits-all value.

---

### 5. Why Redisson RLock (not SETNX)

`SETNX`-based locks require a manually chosen TTL. Too short and the lock expires before the DB query completes, allowing multiple threads to proceed simultaneously and defeating the lock entirely. Too long and a JVM crash leaves the lock held until expiry, blocking all threads for that key.

Redisson's `RLock` uses a watchdog that automatically renews the lease while the lock is held, decoupling lock lifetime from an arbitrary TTL estimate. The lock is only released when explicitly unlocked or when the JVM process dies.

The **double-check** on lock acquisition further reduces unnecessary DB queries: if thread A populated the cache while thread B was waiting for the lock, thread B finds the value on its post-lock cache peek and returns immediately — no duplicate DB read required.


## Tech Stack

| Category | Technology | Purpose |
|---|---|---|
| Framework | Spring Boot 3 | Application framework |
| Cache | Redis + Redisson | Stock deduction, idempotency, product cache |
| Scripting | Lua | Atomic stock deduction and idempotency check |
| Messaging | Apache Kafka | Async order event relay |
| Scheduler | Quartz | Outbox polling, cache warming |
| Resilience | Resilience4j | Circuit breakers per domain |
| Rate Limiting | Sliding Window (Lua) | Per-user request throttling |
| Bloom Filter | Redisson | Cache penetration guard |
| Distributed Lock | Redisson RLock | Cache breakdown protection |
| Persistence | PostgreSQL | Orders, Outbox, Products, SeckillActivities |
| ORM | Spring Data JPA + NamedParameterJdbcTemplate | Repository layer |
| Load Testing | Apache JMeter | Concurrency verification |