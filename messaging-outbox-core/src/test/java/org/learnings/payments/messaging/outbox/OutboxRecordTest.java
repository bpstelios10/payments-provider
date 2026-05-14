package org.learnings.payments.messaging.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRecordTest {

    @Test
    void constructor_setsImmutableFields() {
        OutboxRecord record = new OutboxRecord(42L, "PAYMENT", "CAPTURED", "{\"amount\":100}");

        assertThat(record.getAggregateId()).isEqualTo(42L);
        assertThat(record.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(record.getEventType()).isEqualTo("CAPTURED");
        assertThat(record.getPayload()).isEqualTo("{\"amount\":100}");
    }

    @Test
    void constructor_setsDefaultDeliveryState() {
        OutboxRecord record = new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}");

        assertThat(record.isPublished()).isFalse();
        assertThat(record.getRetryCount()).isEqualTo(0);
        assertThat(record.isFailed()).isFalse();
        assertThat(record.getNextRetryAt()).isNull();
        assertThat(record.getLastError()).isNull();
        assertThat(record.getId()).isNull();
        assertThat(record.getCreatedAt()).isNull();
    }

    @Test
    void setters_updateDeliveryState() {
        OutboxRecord record = new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}");
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Instant nextRetryAt = Instant.now().plusSeconds(10);

        record.setId(id);
        record.setCreatedAt(createdAt);
        record.setPublished(true);
        record.setRetryCount(3);
        record.setNextRetryAt(nextRetryAt);
        record.setFailed(true);
        record.setLastError("connection timeout");

        assertThat(record.getId()).isEqualTo(id);
        assertThat(record.getCreatedAt()).isEqualTo(createdAt);
        assertThat(record.isPublished()).isTrue();
        assertThat(record.getRetryCount()).isEqualTo(3);
        assertThat(record.getNextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(record.isFailed()).isTrue();
        assertThat(record.getLastError()).isEqualTo("connection timeout");
    }
}
