package org.learnings.payments.messaging.outbox.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEvent} entities.
 *
 * <p>Provides the persistence operations needed by {@link OutboxEventProcessorRunner}.
 * The fetch query uses a pessimistic write lock to prevent multiple instances of the
 * application from processing the same events concurrently.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetches up to 100 unprocessed, non-failed events that are due for delivery,
     * acquiring a pessimistic write lock on each row to prevent concurrent processing.
     *
     * <p>Events are ordered by {@code createdAt} ascending to ensure FIFO delivery.
     *
     * @param now the current timestamp used to filter events whose {@code nextRetryAt} has passed
     * @return a list of locked events ready to be sent; never {@code null}
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published = false
              AND failed = false
              AND next_retry_at <= :now
            ORDER BY created_at
            LIMIT 100
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<OutboxEvent> findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(Instant now);
}
