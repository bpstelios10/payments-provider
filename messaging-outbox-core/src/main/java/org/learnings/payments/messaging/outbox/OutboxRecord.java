package org.learnings.payments.messaging.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single outbox entry — an event that needs to be reliably delivered
 * to an external messaging system.
 *
 * <p>This is a pure Java class with no framework dependencies. It holds both the
 * immutable event data (aggregate info, payload) and the mutable delivery state
 * (published, retryCount, etc.) that the {@link OutboxProcessor} updates during processing.
 *
 * <p>Persistence implementations (e.g. JPA) are expected to map this to their own
 * entity type and convert to/from {@code OutboxRecord} as needed.
 */
@SuppressWarnings("unused")
public class OutboxRecord {

    /** Persistent identifier, assigned by the persistence layer. */
    private UUID id;

    /** The identifier of the aggregate that produced this event (e.g. paymentId). */
    private final Long aggregateId;

    /** The type of aggregate that produced this event (e.g. "PAYMENT"). */
    private final String aggregateType;

    /** The type of event (e.g. "INITIATED", "CAPTURED"). */
    private final String eventType;

    /** The serialized event payload. */
    private final String payload;

    /** Timestamp when this record was created, set by the persistence layer. */
    private Instant createdAt;

    /** Whether this event has been successfully sent to the messaging system. */
    private boolean published = false;

    /** How many send attempts have been made so far. */
    private int retryCount = 0;

    /** The earliest time the processor should attempt the next retry. */
    private Instant nextRetryAt;

    /** Whether this record has permanently failed after exhausting all retries. */
    private boolean failed = false;

    /** The error message from the last failed send attempt. */
    private String lastError;

    /**
     * Creates a new outbox record for the given event.
     *
     * @param aggregateId   identifier of the aggregate that raised the event
     * @param aggregateType type name of the aggregate (e.g. "PAYMENT")
     * @param eventType     type name of the event (e.g. "CAPTURED")
     * @param payload       serialized event payload
     */
    public OutboxRecord(Long aggregateId, String aggregateType, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
    }

    /** Gets the persistent identifier. */
    public UUID getId() { return id; }
    /** Sets the persistent identifier. */
    public void setId(UUID id) { this.id = id; }

    /** Gets the identifier of the aggregate that produced this event. */
    public Long getAggregateId() { return aggregateId; }
    /** Gets the type of aggregate that produced this event. */
    public String getAggregateType() { return aggregateType; }
    /** Gets the type of event. */
    public String getEventType() { return eventType; }
    /** Gets the serialized event payload. */
    public String getPayload() { return payload; }

    /** Gets the timestamp when this record was created. */
    public Instant getCreatedAt() { return createdAt; }
    /** Sets the timestamp when this record was created. */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /** Gets whether this event has been successfully sent. */
    public boolean isPublished() { return published; }
    /** Sets whether this event has been successfully sent. */
    public void setPublished(boolean published) { this.published = published; }

    /** Gets the number of send attempts made so far. */
    public int getRetryCount() { return retryCount; }
    /** Sets the number of send attempts made so far. */
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    /** Gets the earliest time the processor should attempt the next retry. */
    public Instant getNextRetryAt() { return nextRetryAt; }
    /** Sets the earliest time the processor should attempt the next retry. */
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    /** Gets whether this record has permanently failed. */
    public boolean isFailed() { return failed; }
    /** Sets whether this record has permanently failed. */
    public void setFailed(boolean failed) { this.failed = failed; }

    /** Gets the error message from the last failed send attempt. */
    public String getLastError() { return lastError; }
    /** Sets the error message from the last failed send attempt. */
    public void setLastError(String lastError) { this.lastError = lastError; }
}
