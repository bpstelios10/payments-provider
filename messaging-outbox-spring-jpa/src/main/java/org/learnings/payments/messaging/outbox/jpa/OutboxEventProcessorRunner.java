package org.learnings.payments.messaging.outbox.jpa;

import org.learnings.payments.messaging.outbox.OutboxProcessor;
import org.learnings.payments.messaging.outbox.OutboxRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Spring-specific runner that bridges JPA persistence with the framework-agnostic
 * {@link OutboxProcessor}.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Fetch pending {@link OutboxEvent} entities from the database (with a pessimistic lock)</li>
 *   <li>Map them to {@link OutboxRecord}s and delegate processing to {@link OutboxProcessor}</li>
 *   <li>Sync the mutated delivery state (published, retryCount, etc.) back onto the entities</li>
 * </ol>
 *
 * <p>The entire operation runs inside a single transaction. Because JPA entities are managed
 * within the transaction, state changes are flushed automatically on commit — no explicit
 * {@code save()} call is needed.
 *
 * <p>This class is intended to be called by {@link OutboxScheduler} or any other trigger
 * mechanism (e.g. an application event, a test utility, etc.).
 */
@Service
public class OutboxEventProcessorRunner {

    private final OutboxEventRepository repository;
    private final OutboxProcessor processor;

    /**
     * Creates a new runner.
     *
     * @param repository the JPA repository for loading and persisting outbox events
     * @param processor  the core processor that handles retry logic and sends records
     */
    public OutboxEventProcessorRunner(OutboxEventRepository repository, OutboxProcessor processor) {
        this.repository = repository;
        this.processor = processor;
    }

    /**
     * Fetches pending outbox events, processes them, and syncs state back to the entities.
     *
     * <p>Runs within a single transaction — entity changes are flushed automatically on commit.
     */
    @Transactional
    public void run() {
        List<OutboxEvent> events = repository
                .findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(
                        Instant.now().truncatedTo(ChronoUnit.MILLIS));

        List<OutboxRecord> records = events.stream().map(OutboxEvent::toRecord).toList();

        processor.process(records);

        for (int i = 0; i < events.size(); i++) {
            syncState(events.get(i), records.get(i));
        }
        // no explicit save needed — entities are managed within the transaction
    }

    private void syncState(OutboxEvent event, OutboxRecord record) {
        event.setPublished(record.isPublished());
        event.setRetryCount(record.getRetryCount());
        event.setNextRetryAt(record.getNextRetryAt());
        event.setFailed(record.isFailed());
        event.setLastError(record.getLastError());
    }
}
