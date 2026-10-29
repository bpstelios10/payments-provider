# messaging-outbox-core

A framework-free implementation of the transactional outbox *processing* logic. Its only dependency is the JDK.

## Contents
| Type | Role |
|---|---|
| `OutboxRecord` | An event to deliver, plus its delivery state (`published`, `retryCount`, `nextRetryAt`, `failed`, `lastError`) |
| `OutboxEventSender` | Port: deliver one record to a broker. **Must throw on failure.** |
| `OutboxProcessor` | Iterates records, calls the sender, and updates delivery state in memory |

## Retry policy
- On failure: `retryCount++`, `nextRetryAt = now + 2^retryCount · 5s` (10s, 20s, 40s, 80s).
- After 5 attempts: `failed = true`. The record is no longer picked up.

## Contract for callers
- Persistence and transactions are the **caller's** job. See `messaging-outbox-spring-jpa`.
- Delivery is at-least-once, so consumers must dedup.

## Usage
```java
OutboxProcessor processor = new OutboxProcessor(record -> kafka.send(...).get());
processor.process(pendingRecords);   // then persist the mutated records
```
