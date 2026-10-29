# Architecture

This document describes the system **as it is today**: its modules, its layering, and the key reliability mechanisms. Design rationale lives in [`adr/`](adr). Known gaps are in [CODE_REVIEW.md](CODE_REVIEW.md), and future direction is in [ROADMAP.md](ROADMAP.md).

## 1. System context

```mermaid
flowchart LR
    M[Merchant backend] -->|REST: create / execute| PS[payments-service]
    PS -->|idempotent call| GW[(Acquirer / PSP gateway<br/><i>stubbed</i>)]
    PS -->|JPA| PDB[(payments DB<br/>payments + outbox_event)]
    PS -. outbox poller .-> K{{Kafka}}
    K -->|PAYMENT_CAPTURED| TS[transactions-service]
    TS -->|JPA| TDB[(transactions DB<br/>ledger_entries)]
```

- **Database per service.** Each service owns its schema. There are no cross-service joins and no distributed transactions.
- **Synchronous edge, asynchronous core.** Merchants talk REST to `payments-service`. Downstream services react to events.

## 2. Modules

| Module | Type | Responsibility |
|---|---|---|
| `payments-service` | Spring Boot app | Payment lifecycle (create, execute), state machine, gateway orchestration, writing outbox events |
| `transactions-service` | Spring Boot app | Consumes payment events and records immutable ledger entries |
| `messaging-outbox-core` | Plain Java lib | Framework-free outbox model (`OutboxRecord`) and delivery/retry logic (`OutboxProcessor`) |
| `messaging-outbox-spring-jpa` | Spring lib | JPA entity, `SKIP LOCKED` polling repository, transactional runner and scheduler for the outbox |
| `messaging-events-core` | Plain Java + Kafka lib | Shared `EventEnvelope`, `EventType`, Kafka JSON (de)serializers |

Dependency direction: apps → libs. The libs never depend on apps, and `*-core` libs never depend on Spring.

## 3. Layering inside a service (clean / hexagonal)

```
adapters/
  inbound/   controllers, Kafka consumers      → call application use cases
  outbound/  Kafka sender, …                   → implement application ports
application/ use cases + ports (PaymentGateway, EventMessagePublisher) + DTOs
domain/      entities, status enums, transition rules, repository interfaces
infrastructure/ framework config, port implementations (gateway client, outbox publisher)
```

Rules:
- Use cases are the transaction boundary. Controllers and consumers only map and delegate.
- Use cases depend on **ports** (interfaces), never on Kafka, HTTP clients or the outbox library directly.
- Pragmatic exception: domain entities are JPA-annotated, and repositories extend Spring Data. This trades purity for less mapping code. See [CODE_REVIEW §19](CODE_REVIEW.md#19-clean-architecture-leaks).

## 4. Payment lifecycle

```mermaid
stateDiagram-v2
    [*] --> INITIATED: POST /payments
    INITIATED --> PROCESSING: execute (claim lease)
    PROCESSING --> PROCESSING: execute after lease timeout (re-claim)
    PROCESSING --> CAPTURED: gateway OK
    PROCESSING --> FAILED: gateway error
    CAPTURED --> [*]
    FAILED --> [*]
```

- Transitions are resolved by `PaymentActionResolver` from `PaymentActionStrategy` implementations.
- Every transition is persisted with a **conditional update** (`WHERE status = :expected`). Concurrent writers cannot overwrite each other.
- `CAPTURED` and `FAILED` are terminal.

## 5. Request flows

### 5.1 Create payment (idempotent)

```mermaid
sequenceDiagram
    participant M as Merchant
    participant C as PaymentsController
    participant U as CreatePaymentUseCase
    participant DB as payments DB
    M->>C: POST /payments {amount, currency, merchantId, idempotencyKey}
    C->>U: execute(dto)
    rect rgb(235,245,255)
    note over U,DB: one transaction
    U->>DB: INSERT payment (INITIATED)
    U->>DB: INSERT outbox_event (INITIATED)
    end
    alt unique violation on idempotency_key
        U->>DB: SELECT by idempotency_key (new tx)
        U-->>C: existing payment
    else
        U-->>C: new payment
    end
    C-->>M: 200 {paymentId, status}
```

The DB unique constraint *is* the lock: there is no check-then-insert race. The insert runs in a narrow `TransactionTemplate`, so the fallback read happens in a clean transaction.

### 5.2 Execute payment

```mermaid
sequenceDiagram
    participant M as Merchant
    participant U as ExecutePaymentUseCase
    participant DB as payments DB
    participant G as PaymentGateway
    M->>U: POST /payments/{id}/execute
    U->>DB: SELECT payment
    alt already terminal
        U-->>M: current state (no-op)
    end
    U->>DB: UPDATE → PROCESSING WHERE INITIATED OR (PROCESSING AND lease expired)
    alt 0 rows
        U-->>M: 409 Conflict (someone else holds the lease)
    end
    note over U,G: no DB transaction open during the call
    U->>G: executePayment(idempotencyKey)
    rect rgb(235,245,255)
    note over U,DB: one transaction
    U->>DB: UPDATE status WHERE status = PROCESSING
    U->>DB: INSERT outbox_event (CAPTURED | FAILED)
    end
    U-->>M: 200 {paymentId, status}
```

Key decisions:
- **No DB transaction spans the external call.** The `PROCESSING` status plus `processing_started_at` act as a lease-based distributed lock instead. See [ADR-0003](adr/0003-lease-based-processing-lock.md).
- The gateway call carries an idempotency key, so a retry after a crash or lease expiry cannot double-charge.

## 6. Transactional outbox

```mermaid
flowchart LR
    subgraph tx[Single DB transaction]
      A[UPDATE payments] --- B[INSERT outbox_event]
    end
    tx --> P[OutboxScheduler<br/>fixed delay]
    P --> R[OutboxEventProcessorRunner<br/>SELECT … FOR UPDATE SKIP LOCKED LIMIT 100]
    R --> O[OutboxProcessor]
    O -->|send| S[OutboxEventSender → Kafka]
    O -->|success| PUB[published = true]
    O -->|error| RET[retry_count++, next_retry_at = now + 2^n·5s]
    RET -->|retry_count ≥ 5| F[failed = true]
```

- **Guarantee:** at-least-once delivery. An event is never lost if its state change is committed. Consumers **must** be idempotent.
- `SKIP LOCKED` lets several instances poll concurrently without processing the same row.
- The core retry logic (`messaging-outbox-core`) is framework-free. Spring/JPA wiring lives in `messaging-outbox-spring-jpa`.

See [ADR-0001](adr/0001-transactional-outbox.md).

## 7. Event contract

Events are wrapped in a common envelope (`messaging-events-core`):

```jsonc
{
  "eventId": "6f1c…",            // consumer idempotency key
  "eventType": "PAYMENT_CAPTURED",
  "payload": { "paymentId": 42, "amount": 10.00, "currency": "EUR", "occurredAt": "…" },
  "occurredAt": "2026-10-31T12:00:00Z"
}
```

- Consumers subclass `AbstractEventEnvelopeDeserializer` and register only the event types they handle.
- `transactions-service` consumes `PAYMENT_CAPTURED` and writes one `ledger_entries` row. It dedups on a unique `event_id`.

> ⚠️ The producer side does not yet emit this envelope. See [CODE_REVIEW §2](CODE_REVIEW.md#2-producer-and-consumer-event-contracts-do-not-match-and-nothing-sends-events).

## 8. Idempotency and concurrency summary

| Concern | Mechanism | Where |
|---|---|---|
| Duplicate create | Unique `idempotency_key` + catch-and-return | `CreatePaymentUseCase`, `payments` table |
| Concurrent execute | Conditional `claimProcessingStatus` lease | `PaymentRepository` |
| Crashed executor | Lease timeout (`service.processing-status-timeout-seconds`) | `ExecutePaymentUseCase` |
| Double charge on retry | Idempotency key forwarded to gateway | `PaymentGateway` |
| Lost-update on status | `UPDATE … WHERE status = :current` | `PaymentRepository` |
| Lost event | Outbox in the same tx as the state change | `OutboxEventPublisher` |
| Duplicate event delivery | Unique `event_id` in the ledger | `ProcessLedgerEntryUseCase` |
| Multi-instance outbox polling | `FOR UPDATE SKIP LOCKED` | `OutboxEventRepository` |

## 9. Runtime and ops

- **Ports:** app on `8080` (context path `/payments-service`, `/transactions-service`) and management on `8081` (`/<service>/private/{health,metrics}`).
- **Schema:** Liquibase changelogs under `src/main/resources/db/changelog`.
- **Images:** distroless Java 25, built from the pre-built `build/libs/service.jar`.
- **CI:** GitHub Actions builds, tests and enforces JaCoCo thresholds. It publishes JUnit and JaCoCo reports to GitHub Pages and comments coverage on PRs.

## 10. Testing strategy

| Level | Scope | Tooling |
|---|---|---|
| Unit | Domain strategies, use cases, outbox core, (de)serializers | JUnit 5, Mockito, AssertJ |
| Component | Full Spring context over HTTP / Kafka, with real DB schema | `@SpringBootTest`, MockMvc, EmbeddedKafka, H2 |
| Coverage gate | Instruction / line / branch / complexity thresholds | JaCoCo (`gradle/jacoco.gradle`) |

The planned next step is Testcontainers (Postgres + Kafka) and contract tests. See [CODE_REVIEW §15](CODE_REVIEW.md#15-tests-run-on-h2-while-production-is-postgres).
