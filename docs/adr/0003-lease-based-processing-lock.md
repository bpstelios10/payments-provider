# ADR-0003: Lease-based PROCESSING lock instead of long DB transactions

**Status:** Accepted. An amendment is pending (see Follow-ups).

## Context
Executing a payment calls an external gateway that may take seconds. Holding a DB transaction or row lock across that call exhausts the connection pool and couples DB health to acquirer latency. Yet two concurrent `execute` calls must not both proceed, and a crashed executor must not leave the payment stuck.

## Decision
- Claim the payment with a single conditional update. The status plus a timestamp act as a **lease**:
  ```sql
  UPDATE payments SET status='PROCESSING', processing_started_at=:now
  WHERE payment_id=:id
    AND (status='INITIATED' OR (status='PROCESSING' AND processing_started_at < :now - timeout))
  ```
- 0 rows means someone else holds the lease, and the use case returns `409`.
- The gateway call runs **outside any DB transaction** and carries an idempotency key, so a re-claim after lease expiry cannot double-charge.
- The terminal update is conditional (`WHERE status='PROCESSING'`) and is committed together with its outbox event.

## Consequences
**Positive**
- No long transactions.
- Self-healing after crashes.
- Lock state is visible and queryable in the DB.

**Negative**
- A lease without a **fencing token** lets a stale holder (lease expired, still running) commit an outcome that conflicts with the new holder.
- The lease uses the JVM clock, so clock skew between instances matters.
- Any gateway exception currently maps to `FAILED`, even for timeouts where the outcome is unknown.

## Follow-ups
- Add an `attempt` (fencing) column, incremented on claim. Terminal updates must match it.
- Use DB `now()` for lease timestamps.
- Publish only when the conditional update affected exactly one row.
- Add a gateway outcome model `Approved | Declined | Unknown`. Leave `Unknown` in `PROCESSING` and resolve it via a status-inquiry or reconciliation job.

## Alternatives considered
- **`SELECT … FOR UPDATE` across the call**: simple, but it holds locks during I/O.
- **Redis/ZooKeeper distributed lock**: adds infrastructure and still needs fencing.
- **Workflow engine (Temporal)**: durable retries and timers. A strong option once flows become multi-step (auth → 3DS → capture).
