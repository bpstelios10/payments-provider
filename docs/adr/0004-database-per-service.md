# ADR-0004: Database per service

**Status:** Accepted

## Context
`payments-service` and `transactions-service` evolve, scale and fail independently. A shared database couples their schemas and release cycles, and lets one service's load affect the other.

## Decision
- Each service owns its schema and is the only one that reads or writes it. Liquibase migrations live inside the service.
- Data crosses boundaries **only** via events (outbox → Kafka) or APIs.

## Consequences
**Positive**
- Independent deployability and schema evolution.
- Fault isolation.
- Clear ownership.

**Negative**
- No cross-service transactions or joins. Consistency is **eventual** and must be designed for (idempotent consumers, reconciliation).
- Reporting across services needs a projection or warehouse (CDC).
- The local docker-compose currently shares a single Postgres instance. Use separate databases or schemas at minimum to keep the boundary honest.
