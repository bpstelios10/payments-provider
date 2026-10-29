# ADR-0002: Idempotent create via DB unique constraint

**Status:** Accepted. An amendment is pending (see Follow-ups).

## Context
Merchants retry `POST /payments` on timeouts. A retry must never create a second payment or a second charge. An application-level check (`SELECT` then `INSERT`) races under concurrency.

## Decision
- The client supplies an `idempotencyKey`, and a **unique constraint** in the `payments` table enforces it.
- The use case inserts the payment and its outbox row in one narrow `TransactionTemplate`.
- On `DataIntegrityViolationException` it reads the existing payment in a new transaction and returns it.
- The use case is deliberately **not** `@Transactional`: the failed insert marks the transaction rollback-only, so the fallback read must run outside it.

## Consequences
**Positive**
- Correct under any concurrency. The DB is the arbiter.
- Retried creates emit no duplicate events, because the outbox insert rolls back with the payment insert.

**Negative**
- The key is currently global, not per merchant.
- A replay with different parameters silently returns the original.
- The key is in the body, not the `Idempotency-Key` header.

## Follow-ups
- Unique `(merchant_id, idempotency_key)`.
- Store a request hash and reject mismatches with `409`/`422`.
- Store and replay the original response, with a status code that distinguishes a replay.
- A TTL and retention policy for keys.
- Use an internal per-attempt key towards acquirers.
