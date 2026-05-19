## Use Case Safety Guarantees

### createPayment

| Concern | Mechanism |
|---|---|
| Idempotency | Unique DB constraint on `idempotencyKey` + catch block returns existing payment |
| Atomicity | `save` + outbox insert in one `TransactionTemplate` |
| Duplicate event on retry | Outbox insert also fails on duplicate — no double event |
| Thread safety | DB constraint is the lock, no shared in-memory state |

### executePayment

| Concern | Mechanism |
|---|---|
| Idempotency on gateway | `idempotencyKey` passed to gateway — safe to call multiple times |
| Concurrent execution prevention | `claimProcessingStatus` with timeout acts as a distributed lock |
| Atomicity of status + event | Status update + outbox insert in one `TransactionTemplate` |
| Stuck PROCESSING recovery | Timeout in `claimProcessingStatus` allows retry after N seconds |
| Gateway succeeds but status update fails | Payment stays PROCESSING — client retries safely, gateway is idempotent |
| Race condition on status update | `setStatusIfCurrentStatusIs` is a no-op if another thread already updated — event is published with actual persisted status |

---

## Error Handling Strategy

The system uses deterministic state transitions combined with idempotent request guarantees to prevent duplicate payment execution.

Primary protections:

* idempotency-key uniqueness
* lifecycle state constraints
* terminal-state locking

---

## Architecture Principles

The service intentionally mirrors production PSP architecture patterns:

* database-per-service
* idempotent write APIs
* immutable ledger boundary (transactions-service)
* event-driven communication via transactional outbox
* state-machine-driven lifecycle transitions

---

## Idempotency-Key Handling

Each payment request must include an `idempotency-key`. The key is stored in the database as a unique value.

Behavior:

```text
first request  → payment created
retry request  → original payment returned (idempotent)
```

Purpose:

* protects against retries after network failures
* prevents duplicate charges
* ensures safe client retry semantics

---

## Payment State Locking

Payment status transitions follow a controlled lifecycle:

```text
INITIATED → PROCESSING → CAPTURED
                       → FAILED
```

Terminal states:

```text
CAPTURED
FAILED
```

Once a payment reaches a terminal state:

* it cannot transition further
* it cannot be modified

This prevents race-condition corruption.

---

## Concurrency Protection Strategy

Concurrency safety relies on:

* `claimProcessingStatus` — conditional update that acts as a distributed lock, with a timeout to recover stuck PROCESSING payments
* DB unique constraint on `idempotencyKey` — prevents duplicate payment creation
* `setStatusIfCurrentStatusIs` — conditional update guarding all terminal state transitions

---

## Transactional Outbox Pattern

Each business state change is paired with an outbox event in a single transaction:

```text
payment write
+
outbox event write
= 1 transaction
```

A scheduler polls the outbox, publishes to Kafka, and marks records as sent. This guarantees:

* no lost events
* no distributed transactions
* reliable service-to-service communication

The outbox mechanism is provided by the `messaging-outbox-spring-jpa` library.

---

## Service Ownership Boundaries

payment-service:

```text
payment lifecycle state
external processor coordination
API orchestration
```

transactions-service:

```text
ledger entries
money movement tracking
```

notification-service (planned):

```text
customer notifications
merchant notifications
```

---

## Money Representation

Amounts are stored using `BigDecimal` + currency string. This prevents floating-point precision errors.

Planned enhancement:

```text
currency-aware scale validation (EUR → 2, JPY → 0)
```

---

## Future Reliability Enhancements

Planned resilience improvements at the gateway adapter boundary:

```text
retry
circuit breaker
timeout handling (timeouts should stay PROCESSING, not transition to FAILED)
bulkhead isolation
```

To be implemented using Resilience4j.

---

## Long-Term Target Architecture

```text
Client
  ↓
payments-service  ──outbox──▶  transactions-service (ledger)
                  ──outbox──▶  notification-service (planned)
                  ──outbox──▶  audit-service (planned)
```

This architecture mirrors production PSP system design patterns.
