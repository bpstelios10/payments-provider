# ADR-0005: Clean / hexagonal layering with pragmatic JPA entities

**Status:** Accepted

## Context
The services should keep business rules testable and independent of transport (HTTP, Kafka) and of infrastructure (gateway clients, outbox).

## Decision
- Package by layer inside each service: `adapters/{inbound,outbound}`, `application` (use cases + ports + DTOs), `domain`, `infrastructure`.
- Use cases depend only on ports: `PaymentGateway`, `EventMessagePublisher`. Adapters and infrastructure implement them.
- **Pragmatic exception:** domain entities are JPA entities, and repository interfaces extend Spring Data `JpaRepository`. This avoids a parallel persistence model and mapping layer while the domain is small.

## Consequences
**Positive**
- Use cases are unit-testable with mocks.
- Kafka and HTTP details stay at the edges.
- Swapping the gateway or the messaging tech touches only adapters.

**Negative**
- The domain depends on JPA and Spring Data annotations.
- Persistence concerns (lazy loading, proxies, equality) leak into the domain.
- Without automated checks, layer rules erode.

## Follow-ups
- Add ArchUnit tests: domain must not depend on adapters, infrastructure or web; application must not depend on web or ORM exceptions.
- Revisit, by splitting domain model and persistence model, once aggregates gain real behaviour (auth/capture/refund).
