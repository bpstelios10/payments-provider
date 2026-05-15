package org.learnings.payments.paymentservice.adapters.outbound.kafka;

import org.junit.jupiter.api.Test;
import org.learnings.payments.messaging.outbox.OutboxRecord;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class KafkaOutboxEventSenderTest {

    private final KafkaOutboxEventSender kafkaOutboxEventSender = new KafkaOutboxEventSender();

    @Test
    void send_succeeds() {
        OutboxRecord event = new OutboxRecord(1L, "", "", "");

        assertDoesNotThrow(() -> kafkaOutboxEventSender.send(event));
    }
}
