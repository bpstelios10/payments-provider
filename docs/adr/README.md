# Architecture Decision Records

Short, immutable records of significant decisions. To change a decision, add a new ADR that supersedes the old one.

| # | Title | Status |
|---|---|---|
| [0001](0001-transactional-outbox.md) | Transactional outbox for inter-service events | Accepted |
| [0002](0002-idempotency-via-db-constraint.md) | Idempotent create via DB unique constraint | Accepted (amendment pending) |
| [0003](0003-lease-based-processing-lock.md) | Lease-based PROCESSING lock instead of long DB transactions | Accepted (amendment pending) |
| [0004](0004-database-per-service.md) | Database per service | Accepted |
| [0005](0005-clean-architecture-layering.md) | Clean / hexagonal layering with pragmatic JPA entities | Accepted |

Template: Context → Decision → Consequences (positive / negative) → Alternatives considered.
