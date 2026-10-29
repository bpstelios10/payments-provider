# ADR-0001: Transactional outbox for inter-service events

**Status:** Accepted

## Context
A payment state change must reliably produce an event for downstream services such as the ledger and notifications. Writing to the DB and publishing to Kafka are two separate systems:
- If we publish first, the DB write can fail, giving a phantom event.
- If we write first, publishing can fail, giving a lost event.

2PC/XA is not available with Kafka and is operationally undesirable.

## Decision
- Write the event to an `outbox_event` table **in the same DB transaction** as the state change.
- A scheduled poller reads unpublished rows with `SELECT … FOR UPDATE SKIP LOCKED LIMIT 100` and sends them.
- On success the row is marked `published`. On failure it is retried with exponential backoff (`2^n · 5s`) and marked `failed` after 5 attempts.

The logic is split into a framework-free core (`messaging-outbox-core`) and a Spring/JPA adapter (`messaging-outbox-spring-jpa`).

## Consequences
**Positive**
- No lost events for any committed state change.
- No distributed transactions.
- Several instances can poll concurrently (`SKIP LOCKED`).
- The core retry logic can be unit-tested without Spring.

**Negative**
- Delivery is **at-least-once**. Every consumer must be idempotent, keyed on a *deterministic* event id.
- Polling adds latency (`outbox.schedule.delay`) and DB load.
- The outbox table grows and needs retention.
- Ordering per aggregate must be designed explicitly: a DB sequence for ordering, Kafka key = aggregate id, and stop-on-failure per aggregate.
- Rows that are `failed` need alerting and a replay tool.

## Alternatives considered
- **Publish after commit** (`@TransactionalEventListener`): loses events on crash.
- **CDC with Debezium** reading the outbox table: lower latency and no polling. It needs Kafka Connect infrastructure. This is the preferred future option at scale.
- **Event sourcing**: a larger paradigm shift than needed now.
