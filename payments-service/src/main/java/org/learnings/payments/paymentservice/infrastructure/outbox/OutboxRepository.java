package org.learnings.payments.paymentservice.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

//    List<OutboxEvent> findTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(Instant before);

    // Dead Letter Queue query for future use
//    List<OutboxEvent> findTop100ByFailedTrueOrderByCreatedAtAsc();

    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published = false
              AND failed = false
              AND next_retry_at <= now()
            ORDER BY created_at
            LIMIT 100
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<OutboxEvent> findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc();
}
