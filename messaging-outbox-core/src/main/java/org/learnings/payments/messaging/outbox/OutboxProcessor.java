package org.learnings.payments.messaging.outbox;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Core processing logic for the transactional outbox pattern.
 *
 * <p>Iterates over a list of pending {@link OutboxRecord}s, attempts to deliver each one
 * via the provided {@link OutboxEventSender}, and updates the record's delivery state
 * (published, retryCount, nextRetryAt, failed) in place.
 *
 * <p>This class has no framework dependencies and can be unit tested without a Spring context.
 * Persistence and transaction management are the responsibility of the caller — see
 * {@code OutboxEventProcessorRunner} in the {@code messaging-outbox-spring-jpa} module
 * for the Spring-specific integration.
 *
 * <p>Retry behaviour:
 * <ul>
 *   <li>On send failure the retry count is incremented and {@code nextRetryAt} is set
 *       using exponential backoff (10s → 20s → 40s → 80s → ...)</li>
 *   <li>After {@value MAX_RETRIES} failed attempts the record is marked as permanently
 *       failed and will no longer be retried</li>
 * </ul>
 */
public class OutboxProcessor {

    private static final int MAX_RETRIES = 5;

    private final OutboxEventSender sender;

    /**
     * Creates a new processor backed by the given sender.
     *
     * @param sender the sender used to deliver outbox records; never {@code null}
     */
    public OutboxProcessor(OutboxEventSender sender) {
        this.sender = sender;
    }

    /**
     * Processes the given list of pending outbox records.
     *
     * <p>For each record: attempts delivery via the sender, marks it as published on
     * success, or increments the retry count (and eventually marks it failed) on error.
     * State is mutated directly on each {@link OutboxRecord} — the caller is responsible
     * for persisting the changes.
     *
     * @param records the list of records to process; must not be {@code null}
     */
    public void process(List<OutboxRecord> records) {
        for (OutboxRecord record : records) {
            try {
                sender.send(record);
                record.setPublished(true);
            } catch (Exception ex) {
                handleRetry(record, ex);
            }
        }
    }

    private void handleRetry(OutboxRecord record, Exception ex) {
        int retry = record.getRetryCount() + 1;
        record.setRetryCount(retry);

        if (retry >= MAX_RETRIES) {
            record.setFailed(true);
            record.setLastError(ex.getMessage());
            return;
        }

        Instant nextRetryAt = Instant.now().plusSeconds(backoff(retry)).truncatedTo(ChronoUnit.MILLIS);
        record.setNextRetryAt(nextRetryAt);
    }

    private long backoff(int retry) {
        // 10s → 20s → 40s → 80s → ...
        return (long) Math.pow(2, retry) * 5;
    }
}
