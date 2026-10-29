# Roadmap — towards a production PSP

This is a high-level view. Each phase builds on the previous one. Fix the P0 items in [CODE_REVIEW.md](CODE_REVIEW.md) before starting Phase 1.

## Phase 0 — Harden what exists
- Close the P0 and P1 findings: event contract, fencing, unknown outcomes, auth, `ddl-auto`, DLQ.
- Testcontainers, contract tests, concurrency tests, ArchUnit.
- OpenTelemetry tracing across HTTP and Kafka, JSON logs, RED and business metrics, SLOs and alerts.
- A real local stack in docker-compose: Postgres per service, Kafka, Prometheus/Grafana.

## Phase 1 — Core payments domain
- **Payment Intent / Attempt model.** One intent can have many attempts (retries, payment-method switches, acquirer failover).
- **Authorization ≠ capture.** States `REQUIRES_PAYMENT_METHOD → REQUIRES_ACTION (3DS) → AUTHORIZED → CAPTURED`, plus partial capture, void and authorization expiry.
- **Refunds and reversals** (full or partial) as their own aggregates with their own lifecycle.
- A `Money` value object with ISO-4217 exponents and minor units.
- A **public API**: versioned (`/v1`), OpenAPI spec-first, RFC 9457 errors, cursor pagination, and merchant-scoped idempotency with request hashing and a response cache.
- **Webhooks** to merchants: signed (HMAC), retried with backoff, replayable, with a per-merchant endpoint config.

## Phase 2 — Money movement and the books
- A **double-entry ledger**: accounts, postings and journal entries. Append-only and balanced per currency. Balances are derived from postings, never stored.
- **Fees and pricing engine:** interchange++, blended, per-merchant pricing plans.
- **Settlement:** ingest acquirer settlement files and match them against the ledger.
- **Reconciliation** (three-way: internal ledger ↔ acquirer ↔ bank statement), with breaks surfaced to an ops queue.
- **Payouts** to merchants: schedules, reserves, rolling holds, negative balance handling.
- **Multi-currency and FX:** rate locking, markup, FX ledger accounts.

## Phase 3 — Connectivity
- An **acquirer abstraction** with several connectors (card schemes via acquirers, SEPA, Faster Payments, wallets).
- **Smart routing:** pick the acquirer by cost, auth rate, BIN, region and health. Failover on soft declines.
- **Status inquiry and late-response handling** for every connector, to resolve `Unknown` outcomes.
- **Network tokens and account updater**, to raise auth rates.

## Phase 4 — Security and compliance
- A **card vault / tokenization service** in an isolated PCI DSS CDE, with HSM/KMS-backed encryption and short-lived detokenization. All other services see only tokens.
- **SCA / 3DS2** orchestration (frictionless and challenge flows, exemptions).
- **Merchant onboarding: KYC/KYB**, sanctions and PEP screening, ongoing monitoring.
- **AML** transaction monitoring and case management.
- Secrets in Vault/KMS, mTLS between services, least-privilege IAM, audit log (immutable, append-only).
- Data residency, GDPR erasure versus financial-record retention, encryption at rest and in transit.

## Phase 5 — Risk
- A **fraud engine**: synchronous risk scoring before authorization (rules plus ML), velocity checks, device fingerprinting, a merchant risk profile.
- **Disputes and chargebacks:** scheme notifications, an evidence workflow, a ledger impact (debit merchant), reason-code analytics.

## Phase 6 — Merchant experience
- Dashboard: payments search, refunds, disputes, payouts, reports, API keys, webhooks, team/RBAC.
- Reporting: transaction exports and financial summaries from a read-optimised store (CQRS projection or warehouse).
- SDKs (server and client), hosted checkout and payment links, platform plugins (Shopify, WooCommerce).
- A sandbox environment with test cards and simulated scheme behaviour.

## Cross-cutting platform concerns
| Area | Direction |
|---|---|
| Data | Postgres per service. Partition high-volume tables (payments, ledger, outbox) by time. Read replicas for queries. CDC (Debezium) into a warehouse. |
| Messaging | Kafka with a schema registry (Avro/Protobuf), compatibility checks in CI, a DLT per consumer, replay tooling. |
| Workflows | Consider a durable workflow engine (Temporal) for long-running flows such as auth→capture, refunds and payouts. |
| Resilience | Timeouts everywhere, circuit breakers and bulkheads per acquirer, load shedding, regional isolation (cells). |
| Deployment | Kubernetes, GitOps, canary and progressive delivery, feature flags, zero-downtime migrations (expand/contract). |
| Availability | Multi-AZ first, then multi-region active/passive for the payment API, with RPO 0 for the ledger. |
| Security in SDLC | CodeQL, dependency and container scanning, SBOM, signed images, SHA-pinned actions, threat modelling per feature. |
| Operations | Runbooks, on-call, game days and chaos testing, error budgets, incident postmortems. |
