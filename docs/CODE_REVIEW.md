# Code Review — payments-provider

Reviewed at commit `913880c`. The bar is a production PSP (Stripe / Adyen / Revolut level).

Severity scale:

| Level | Meaning |
|---|---|
| **P0** | Money correctness, data loss or security. Blocks production. |
| **P1** | Reliability or operability gap. Must fix before real traffic. |
| **P2** | Design or maintainability. Fix soon. |
| **P3** | Nits and hygiene. |

---

## What is already good

- **Transactional outbox.** The state change and its event are written in one DB transaction.
- **Idempotent create.** A DB unique constraint plus catch-and-return. There is no read-then-write race.
- **Conditional status updates** (`UPDATE ... WHERE status = :current`). There is no read-modify-write race on status.
- **`FOR UPDATE SKIP LOCKED` outbox polling.** Several instances can poll without blocking each other.
- **Lease-based PROCESSING claim**, so a crashed executor does not leave a payment stuck forever.
- **No DB transaction is held across the gateway call.** This is the right call.
- **Consumer-side dedup** on `eventId` in the ledger.
- **`BigDecimal`** for money, never `double`.
- **Framework-free outbox core**, with Spring/JPA in an adapter module.
- **Liquibase migrations, a distroless image, high coverage gates, and component tests with EmbeddedKafka.**

The fundamentals are right. Most findings below are about edge cases in exactly these mechanisms. In payments, those edge cases are where money gets lost.

---

## P0 — Correctness, data loss, security

### 1. Hibernate `ddl-auto: create` runs alongside Liquibase
`payments-service/src/main/resources/application.yml`, `transactions-service/src/main/resources/application.yml`

Liquibase creates the schema, then Hibernate **drops and recreates it** at startup.
- On Postgres, every restart wipes all payments and outbox rows.
- The Liquibase-only objects are lost: the partial index `idx_outbox_polling_partial`, `idx_payments_status_processing_started`, and the column defaults.
- Tests therefore run against a Hibernate-generated schema, not the migrated one.

**Fix:** set `ddl-auto: validate` in every profile. Liquibase is the only schema owner.

### 2. Producer and consumer event contracts do not match, and nothing sends events
`OutboxEventPublisher`, `KafkaOutboxEventSender`, `PaymentCapturedEventConsumer`

| | Producer (payments-service) | Consumer (transactions-service) |
|---|---|---|
| Transport | `System.out.println` (stub) | Kafka topic `PAYMENT_CAPTURED` |
| Event type | `"CAPTURED"` (status name) | `EventType.PAYMENT_CAPTURED` |
| Shape | Raw `Payment` JPA entity as JSON | `EventEnvelope{eventId, eventType, payload, occurredAt}` |
| Payload | `paymentId, amount, …, createdDate, updatedDate, idempotencyKey, processingStartedAt` | `paymentId, amount, currency, occurredAt` |

Once the Kafka sender is wired, every message will fail to deserialize. Serializing the JPA entity also leaks internal schema, including the merchant's idempotency key, into a public contract.

**Fix:**
- Define explicit, versioned event schemas, for example `PaymentCaptured.v1`, in `messaging-events-core`.
- Have the producer build the `EventEnvelope`, with `eventId = outbox row id` (see #3).
- Add a contract test, or a schema registry with compatibility checks.

### 3. Duplicate `CAPTURED` events can double-credit the ledger
`ExecutePaymentUseCase.updateStatusAndPublishEvent`, `ProcessLedgerEntryUseCase`

The code comment says paymentId+status acts as the idempotency key. Nothing enforces that:
- `setStatusIfCurrentStatusIs(...)`'s return value is **ignored**. The event is published even when this thread did not perform the transition.
- `outbox_event` has **no unique constraint** on `(aggregate_type, aggregate_id, event_type)`.
- The consumer dedups on `eventId`, which is a new value per outbox row.

Here is how it happens. Thread A's lease expires (the gateway is slow, >10s). Thread B re-claims. A finishes and sets CAPTURED, then publishes. B finishes, its conditional update affects 0 rows, it re-reads CAPTURED, and **publishes CAPTURED again**. The ledger receives two distinct eventIds and books two credits.

**Fix (defence in depth):**
- Publish only if `updatedRows == 1`.
- Add a unique constraint on the outbox `(aggregate_type, aggregate_id, event_type)`, or on a deterministic event id such as `UUIDv5(paymentId + transition)`.
- In the ledger, also add a unique natural key, `(payment_id, entry_type)`.

### 4. Lease expiry has no fencing, so concurrent gateway calls race
`PaymentRepository.claimProcessingStatus`

A lease (`processing_started_at < now - 10s`) with no fencing token means two executors can be "inside" the critical section at once. The gateway's idempotency makes the *charge* safe. The *outcome* is not: if A fails (network timeout, marked FAILED) and B succeeds (CAPTURED), the first writer wins. The DB can say FAILED while the customer was charged.

**Fix:**
- Add an `attempt`/`version` column and increment it on claim.
- Make terminal updates `WHERE status = PROCESSING AND attempt = :myAttempt`. A stale holder can then never commit.
- Use **DB time** (`now()`) for the lease, not JVM `Instant.now()`, to avoid clock skew between instances.

### 5. Every gateway exception becomes FAILED
`ExecutePaymentUseCase.execute` (it carries a TODO)

A timeout, connection reset or 5xx is an **unknown outcome**: the issuer may have approved. Marking it FAILED means "customer charged, merchant told it failed", which is the worst outcome in payments.

**Fix:**
- Classify gateway results into `Approved | Declined(reason) | Unknown`.
- Map `Unknown` to staying `PROCESSING`, or a dedicated `PENDING_CONFIRMATION`, resolved by a **status-inquiry / reconciliation job** against the acquirer.
- Only use FAILED for definitive declines.

### 6. No authentication, and IDOR on sequential IDs
`PaymentsController`

- `merchantId` comes from the request **body**, so any caller can create payments as any merchant.
- `POST /payments/{paymentId}/execute` takes a sequential `Long`. That makes enumeration and capture of other merchants' payments trivial.

**Fix:**
- Authenticate merchants (API keys / OAuth2 client credentials / mTLS) and derive `merchantId` from the principal.
- Scope every query by merchant.
- Expose opaque IDs (`pay_01J…` ULID/UUIDv7) externally.

### 7. Idempotency is global and has no request fingerprint
`Payment` unique constraint, `CreatePaymentUseCase`

- The key is unique **globally**, so merchant A's key can collide with merchant B's and return B's payment to A (a data leak). Scope it to `(merchant_id, idempotency_key)`.
- A replay with **different parameters** (another amount) silently returns the original payment. Stripe-style behaviour is to store a request hash and return `409`/`422` on mismatch.
- The key belongs in the `Idempotency-Key` **header**, not the body. It needs a TTL/retention policy, and it should be a string rather than only a UUID.
- The merchant's key is forwarded to the acquirer as-is. Use an internal per-attempt key instead.

### 8. Actuator and H2 console expose secrets
`application.yml` (both services)

- `env`, `configprops` and `heapdump` are exposed. A heap dump contains DB credentials and, later, card data.
- The H2 console is enabled with `web-allow-others: true`.
- `show-sql: true` logs queries.

**Fix:** expose only `health,prometheus` by default. Enable the rest via a `local` profile only, and protect the management port at the network level.

---

## P1 — Reliability and operability

### 9. The outbox runner holds a DB transaction and row locks across network I/O
`OutboxEventProcessorRunner.run()` is `@Transactional` around up to 100 sequential `send()` calls.
- A slow broker means long-held locks and connection-pool starvation.
- If the commit fails after sends succeeded, all 100 events are re-sent. This is acceptable for at-least-once delivery, but only if consumers are truly idempotent (see #3).
- `last_error` is `VARCHAR(1000)` and the exception message is not truncated. A long message makes the **commit fail**, which rolls back `published=true` for the whole batch. That produces mass redelivery and a hot loop.

**Fix:**
- Truncate `lastError`.
- Use small batches, or claim-then-release (mark `in_flight` with a lease, commit, send, then update per row).
- Use synchronous Kafka sends with a bounded timeout.

### 10. Outbox ordering is not preserved per aggregate
- `OutboxProcessor` keeps going after a failed record, so a later event for the same payment can be sent before an earlier one.
- `ORDER BY created_at` uses the JVM `@CreationTimestamp`, which is not monotonic across instances.

**Fix:**
- Order by a DB sequence.
- On failure, skip the remaining records for **that aggregate** in the batch.
- Use `aggregateId` as the Kafka key so per-payment ordering holds on a single partition.

### 11. Kafka producer is not implemented
`KafkaOutboxEventSender` is a stub. When implementing it:
- Use `acks=all` and `enable.idempotence=true`.
- Set key = `aggregateId`.
- Block on `send().get(timeout)` so failures reach the retry logic.
- Propagate trace headers.

### 12. Consumer has no error handling or DLQ, and offsets reset to `latest`
`KafkaConsumerConfig`
- There is no `ErrorHandlingDeserializer`. A poison message fails on every poll.
- Default `DefaultErrorHandler` retries 10 times, then **logs and skips**, so a ledger entry is silently lost. Configure a `DeadLetterPublishingRecoverer` with a DLT and alerting.
- `auto.offset.reset` defaults to `latest`, so a new consumer group misses history. Use `earliest`.
- Generic types are wrong: `ConsumerFactory<String, String>` actually yields `EventEnvelope`.
- The consumer is annotated `@Controller`. It should be `@Component`.

### 13. The "ledger" is a single-entry event log, not a ledger
`LedgerEntry` has no `merchant_id`, no account, and no debit/credit side, so "credits merchant" cannot happen. Real PSP ledgers are **double-entry**:
- Every movement is ≥2 postings that sum to zero per currency, across accounts such as `customer_funds`, `merchant_payable`, `fees`, `scheme_settlement`.
- They are append-only and corrected by reversal entries.
- Balances are derived from postings.

This is the foundation for settlement, payouts, fees and reconciliation.

### 14. Failed outbox events have no recovery path
After 5 retries a row is set to `failed=true` and nothing else happens. There is no metric, no alert, no replay endpoint and no retention or cleanup. Add:
- `outbox_pending_count` and `outbox_oldest_unpublished_age_seconds` gauges
- alerts on `failed > 0`
- an admin replay endpoint
- partitioning or TTL cleanup of published rows

### 15. Tests run on H2 while production is Postgres
The tests do not exercise:
- `FOR UPDATE SKIP LOCKED`
- partial indexes
- `TIMESTAMP` vs `TIMESTAMPTZ` semantics
- the `PostgreSQLDialect`-on-H2 mismatch

Use **Testcontainers** (Postgres plus Kafka) for component tests. Add a real concurrency test: N threads executing the same payment should give exactly one gateway outcome and exactly one terminal event.

### 16. Gateway resilience is missing
The gateway call has no timeouts, circuit breaker, bulkhead or retry budget. Add Resilience4j at the adapter, with timeouts that map to `Unknown` (see #5).

### 17. Observability is limited
- There are no correlation or trace IDs. Use OpenTelemetry and propagate through Kafka headers.
- Logs are not structured as JSON.
- There are no business metrics: payments by status, auth rate, gateway latency, outbox lag.
- `payments-service` has no `logback-spring.xml`.
- `ProcessLedgerEntryUseCase` logs `"Show me the error"`.

### 18. Local environment is broken
- `DEPLOYMENT_ENVIRONMENT=docker-compose` is not mapped to `SPRING_PROFILES_ACTIVE`, so the payments-service in compose **still runs on H2**.
- `transactions-service` in compose has no Postgres and no Kafka; it expects `localhost:9094`.
- `depends_on` has no healthchecks.
- The Dockerfile needs a pre-built jar. Use a multi-stage build or document `./gradlew build` first.

---

## P2 — Design and maintainability

### 19. Clean-architecture leaks
- `domain.Payment` is a JPA entity, and `domain.repositories.PaymentRepository` extends `JpaRepository` with JPQL. The domain depends on Spring Data. Either accept this pragmatically (and document it), or split into a domain model, a port and a JPA adapter.
- The application layer throws `ResponseStatusException` (web) and `ObjectOptimisticLockingFailureException` (ORM). Throw domain exceptions such as `PaymentNotFound` and `PaymentBusy`, and map them to HTTP in a `@RestControllerAdvice`.
- The status-transition strategies are Spring `@Component`s. That is domain logic depending on the container.
- Add **ArchUnit** tests to enforce the layer rules.

### 20. Over-engineered, anemic state machine
Four strategy classes plus a resolver encode a five-row transition table. Prefer a rich aggregate (`payment.startProcessing()`, `payment.capture()`, `payment.fail(reason)`) backed by an `EnumMap<Status, Set<Status>>`. Invalid transitions should throw. `PaymentStatusAction.CREATE` is unused.

### 21. The domain model is too thin for a PSP
- Authorization and capture are collapsed into one step; there is no `AUTHORIZED`.
- There are no refunds, voids, partial captures, or `REQUIRES_ACTION` for 3DS/SCA.
- There is no `PaymentAttempt` entity. Real systems separate the *intent* (merchant's view) from the *attempts* (per acquirer call).

### 22. Money handling
- `currency` is optional and defaults to `"USD"` silently. Make it required and validate it against ISO-4217.
- Scale is fixed at 2. JPY has 0 decimals and KWD/BHD have 3.
- Introduce a `Money` value object, or store **minor units as `long`** (Stripe does this) with a currency-exponent table.

### 23. API design
- There is no `GET /payments/{id}`, so merchants cannot poll status. There is no list or pagination.
- There is no versioning (`/v1`).
- Create returns `200`. Return `201`, and replay the *same* stored response on idempotent retry.
- Errors are ad-hoc. Use RFC 9457 `ProblemDetail` with stable machine-readable error codes. Drop the `ServiceConfigurationError` handler.
- There is no OpenAPI spec. Write spec-first and generate the docs.

### 24. JPA hygiene
- Lombok `@EqualsAndHashCode` on entities (`Payment`, `OutboxEvent`, `LedgerEntry`) breaks with generated IDs, Hibernate proxies and Sets. Use ID-based equality or none.
- Use `TIMESTAMP WITH TIME ZONE` (`timestamptz`) for all instants.
- Add `@Version` where entities are updated via JPA.

### 25. Messaging library design
- Replace component scanning (`scanBasePackageClasses`, `@EntityScan` of library packages) with **Spring Boot auto-configuration** (`META-INF/spring/...AutoConfiguration.imports`) and `@ConfigurationProperties`.
- `OutboxScheduler` has `@EnableScheduling` while its Javadoc says the host app must add it. Pick one; auto-configuration is the better home.
- Make `MAX_RETRIES` and the backoff configurable, and add jitter.
- The Javadoc `@throws Exception` on `OutboxEventSender.send` does not match the signature.
- `aggregateId` is typed `Long`. Use `String` so the library is generic.
- `EventEnvelope` uses `@JsonTypeInfo(use = Id.CLASS)`. That leaks Java class names onto the wire and is a known deserialization-gadget risk. The type registry already resolves payloads, so remove it.
- A global `EventType` enum in a shared jar couples every service release. Prefer per-domain schemas (Avro/Protobuf/JSON Schema) and a schema registry.
- Consider **CDC (Debezium)** instead of polling at scale.

### 26. Build and dependencies
- The project runs on Spring Boot `4.1.0-M4` and Micrometer `1.17.0-M3`, which are **milestones**. Pin GA versions for anything that handles money.
- Versions the BOM already manages are re-pinned manually (`kafkaClients`, `jackson`, `junit`, `mockito`, …), which risks drift. Let the BOM manage them.
- Enable virtual threads (`spring.threads.virtual.enabled=true`) on Java 25 instead of 500 Tomcat threads.

### 27. CI/CD
- Workflow-level permissions are broad (`pages: write`, `contents: write`). Use least privilege per job.
- Third-party actions are pinned by tag, not SHA.
- There is no CodeQL, dependency scanning (the job is commented out), container scan or SBOM.
- The pipeline does not build or publish images, and has no deploy stages or environments.
- Per-module report steps are copy-pasted and skip the libraries. Use a matrix.
- The workflow name is still `spring-web-template Pipeline`.
- Coverage gates of 93–97% encourage tests that only chase coverage. Add **PIT mutation testing** for the domain and outbox core instead.

---

## P3 — Nits

- The Liquibase precondition has a typo, `tableName: outbox_eventauthor`, so it never matches.
- The `MARK_RAN on tableExists` preconditions hide schema drift. Prefer strict changesets.
- `Payment` comment says "(id-key, payment-id) unique" but the constraint is on the id-key only.
- The `PaymentRepository` overrides of `save` and `findById` are noise.
- `PROCESSING_STATUS_TIMEOUT_DURATION_SECS` should be a camelCase final field injected via `@ConfigurationProperties`.
- `OutboxProcessor` records `lastError` only on the final failure. Record it on every attempt.
- The root README still says transactions-service is "planned".

---

## Suggested order of work

1. **P0 #1** (`ddl-auto: validate`) and **#8** (actuator/H2). These are config-only and quick.
2. **#2, #3, #11**: event contract, deterministic event IDs, publish-only-on-transition, a real Kafka producer, and a contract test.
3. **#4, #5, #16**: fencing token, `Unknown` outcome plus status inquiry, Resilience4j.
4. **#6, #7**: auth, merchant-scoped idempotency with request hash, opaque IDs.
5. **#9, #10, #12, #14**: outbox and consumer hardening, plus outbox metrics and alerts.
6. **#15**: Testcontainers and concurrency tests, to lock all of the above in.
7. **#13, #21, #22**: double-entry ledger, auth/capture split, `Money`.

See [ROADMAP.md](ROADMAP.md) for the longer-term platform view.
