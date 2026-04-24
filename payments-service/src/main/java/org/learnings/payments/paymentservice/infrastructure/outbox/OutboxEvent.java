package org.learnings.payments.paymentservice.infrastructure.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@EqualsAndHashCode
public class OutboxEvent {

    public OutboxEvent(Long aggregateId, String aggregateType, String eventType, String payload) {
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
    private final boolean published = false;
}
