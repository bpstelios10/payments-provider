#  Payments Service Provider

## Overview

`payments-provider` is a learning-focused Payment Service Provider (PSP) style microservices architecture project designed to simulate real-world payment processing workflows.

The goal of the project is to explore:

* payment lifecycle orchestration
* idempotent API design
* fault-tolerant service boundaries
* event-driven microservices communication
* ledger-based transaction tracking (upcoming)
* Java concurrency patterns
* resilience techniques used in production fintech systems

The system currently includes:

| Module | Type | Status |
|---|---|---|
| [`payments-service`](payments-service) | Service: payment lifecycle, gateway orchestration, outbox | Implemented (gateway + Kafka sender stubbed) |
| [`transactions-service`](transactions-service) | Service: consumes payment events, writes ledger entries | Implemented (single-entry ledger) |
| [`messaging-outbox-core`](messaging-outbox-core) | Library: framework-free outbox processing | Implemented |
| [`messaging-outbox-spring-jpa`](messaging-outbox-spring-jpa) | Library: Spring/JPA outbox polling | Implemented |
| [`messaging-events-core`](messaging-events-core) | Library: event envelope + Kafka (de)serializers | Implemented |
| notification-service | Service | Planned |

Architecture follows the **database-per-service** microservices pattern.

## Documentation

* [Architecture](docs/ARCHITECTURE.md): modules, layering, flows, outbox, idempotency
* [Architecture Decision Records](docs/adr): why things are the way they are
* [Code Review](docs/CODE_REVIEW.md): prioritised gaps against a production PSP bar
* [Roadmap](docs/ROADMAP.md): path towards a full payments provider
* [payments-service tech notes](payments-service/TECH.md)

## Build & Run

Requires JDK 25 and Docker.

```bash
./gradlew build                  # compile, test, enforce JaCoCo coverage
./gradlew :payments-service:bootRun
docker compose up --build        # requires ./gradlew build first (images copy build/libs/service.jar)
```

* Business API: `http://localhost:8090/payments-service/payments`, which is payments-service in compose
* Health/metrics: management port `8081`, `/<service>/private/health` and `/<service>/private/metrics`

---

## Targeted Domains

Implementation of a PSP that offers the following functionality:

1. Payment Processing
  Accept payments (cards, wallets, bank transfers)
  Authorization & capture
  Refunds & reversals

2. Merchant Management
  Merchant onboarding (KYC/KYB)
  Account setup & configuration
  Dashboard & reporting

3. Payment Methods
  Credit/debit cards
  Digital wallets (Apple Pay, Google Pay)
  Local payment methods (e.g., SEPA in Europe)

4. Risk & Compliance
  Fraud detection & prevention
  PCI DSS compliance
  AML / KYC checks

5. Transaction Management
  Payment status tracking
  Settlement & reconciliation
  Payouts to merchants

6. APIs & Integrations
  REST APIs / SDKs
  Webhooks for events
  Plugins (e.g., Shopify, WooCommerce)

7. Security
  Tokenization of card data
  Encryption
  Authentication (3D Secure, SCA)

8. Reporting & Analytics
  Transaction reports
  Financial summaries
  Dispute/chargeback tracking

9. Dispute Handling
  Chargeback management
  Evidence submission workflows
