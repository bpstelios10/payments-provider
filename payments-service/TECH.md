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
* immutable ledger boundary (future transaction-service)
* event-driven communication (planned outbox pattern)
* state-machine-driven lifecycle transitions

---

## Idempotency-Key Handling

Each payment request must include:

```http
Idempotency-Key
```

The key is stored in the database as a unique value.

Behavior:

```text
first request → payment created
second identical request → rejected
```

Future improvement (planned):

```text
second identical request → original response replayed
```

This mirrors production PSP behavior.

Purpose:

* protects against retries after network failures
* prevents duplicate charges
* ensures safe client retry semantics

---

## Payment State Locking

Payment status transitions follow a controlled lifecycle:

```text
CREATED → PROCESSING → SUCCESS
                       → FAILED
```

Terminal states:

```text
SUCCESS
FAILED
```

Once a payment reaches a terminal state:

* it cannot transition further
* it cannot be modified

This prevents race-condition corruption.

---

## Concurrency Protection Strategy

Concurrency safety relies on:

* database constraints
* lifecycle validation
* unique idempotency keys

Future enhancement:

```text
optimistic locking via version column
```

This will prevent conflicting updates during processor callbacks.

---

## Planned Outbox Pattern

Upcoming architecture change:

```text
payment write
+
outbox event write
=
1 transaction
```

Publisher worker:

```text
poll outbox
publish event
mark sent
```

This guarantees:

* no lost events
* no distributed transactions
* reliable service-to-service communication

---

## Service Ownership Boundaries

Service responsibilities are clearly separated:

payment-service:

```text
payment lifecycle state
external processor coordination
API orchestration
```

transaction-service (planned):

```text
ledger entries
account balances
money movement tracking
```

notification-service (planned):

```text
customer notifications
merchant notifications
```

This matches real-world PSP architecture boundaries.

---

## Money Representation

Amounts are stored using:

```text
BigDecimal
currency
```

This prevents floating-point precision errors.

Future enhancement:

```text
currency-aware scale validation
```

Example:

```text
EUR → scale 2
JPY → scale 0
```

Required for production-grade ledger correctness.

---

## Future Reliability Enhancements

Planned resilience improvements:

```text
retry
circuit breaker
timeout
bulkhead isolation
```

These will be implemented using:

```text
Resilience4j
```

Applied at the processor adapter boundary.

---

## Long-Term Target Architecture

Final system flow:

```text
Client
  ↓
payment-service
  ↓
outbox event
  ↓
transaction-service
  ↓
double-entry ledger
  ↓
notification-service
```

This architecture mirrors production PSP system design patterns.
