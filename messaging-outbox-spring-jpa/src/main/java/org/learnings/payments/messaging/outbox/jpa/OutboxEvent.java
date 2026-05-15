package org.learnings.payments.messaging.outbox.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.learnings.payments.messaging.outbox.OutboxRecord;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * JPA entity representing a persisted outbox record in the {@code outbox_event} table.
 *
 * <p>This is the Spring Data JPA counterpart to {@link OutboxRecord}. It carries the
 * same fields but with JPA and Hibernate annotations for persistence.
 *
 * <p>Use {@link #fromRecord(OutboxRecord)} to create an entity from a core record,
 * and {@link #toRecord()} to convert back — for example before passing to
 * {@link org.learnings.payments.messaging.outbox.OutboxProcessor}.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@EqualsAndHashCode
public class OutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;
    private Long aggregateId;
    private String aggregateType;
    private String eventType;
    @Column(columnDefinition = "TEXT")
    private String payload;
    @SuppressWarnings("UnusedDeclaration")
    @CreationTimestamp
    private Instant createdAt;
    @Setter
    private boolean published = false;
    @Setter
    private int retryCount = 0;
    @Setter
    private Instant nextRetryAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    @Setter
    private boolean failed = false;
    @Setter
    private String lastError;

    /**
     * Creates a new {@code OutboxEvent} entity from the given {@link OutboxRecord}.
     *
     * @param record the core outbox record to persist; never {@code null}
     * @return a new entity populated with the record's immutable event data
     */
    public static OutboxEvent fromRecord(OutboxRecord record) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateId = record.getAggregateId();
        event.aggregateType = record.getAggregateType();
        event.eventType = record.getEventType();
        event.payload = record.getPayload();
        return event;
    }

    /**
     * Converts this entity to a {@link OutboxRecord}, including full delivery state.
     *
     * @return a new {@code OutboxRecord} reflecting the current state of this entity
     */
    public OutboxRecord toRecord() {
        OutboxRecord record = new OutboxRecord(aggregateId, aggregateType, eventType, payload);
        record.setId(id);
        record.setCreatedAt(createdAt);
        record.setPublished(published);
        record.setRetryCount(retryCount);
        record.setNextRetryAt(nextRetryAt);
        record.setFailed(failed);
        record.setLastError(lastError);
        return record;
    }
}
