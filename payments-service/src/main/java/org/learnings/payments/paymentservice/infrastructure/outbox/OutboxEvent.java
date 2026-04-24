package org.learnings.payments.paymentservice.infrastructure.outbox;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.learnings.payments.paymentservice.services.ports.EventMessage;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@EqualsAndHashCode
public class OutboxEvent {

    OutboxEvent(Long aggregateId, String aggregateType, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
    }

    @Id
    @GeneratedValue
    private UUID id;
    private Long aggregateId; // paymentId
    private String aggregateType; // PAYMENT
    private String eventType; // PAYMENT STATUS
    @Column(columnDefinition = "TEXT")
    private String payload;
    @SuppressWarnings({"UnusedDeclaration"})
    @CreationTimestamp
    private Instant createdAt;
    @Setter
    private boolean published = false;
    @Setter
    private int retryCount = 0;
    @Setter
    private Instant nextRetryAt = Instant.now();
    @Setter
    private boolean failed = false;
    @Setter
    private String lastError;

    static OutboxEvent fromEventMessage(EventMessage event) {
        return new OutboxEvent(event.aggregateId(), event.aggregateType(), event.eventType(), event.payload());
    }
}
