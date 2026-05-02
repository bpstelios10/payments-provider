package org.learnings.payments.paymentservice.integration.kafka;

import org.junit.jupiter.api.Test;
import org.learnings.payments.paymentservice.infrastructure.outbox.OutboxEvent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class KafkaOutboxEventSenderTest {

    private final KafkaOutboxEventSender kafkaOutboxEventSender = new KafkaOutboxEventSender();

    @Test
    void send_succeeds() {
        OutboxEvent event = new OutboxEvent(1L, "", "", "");

        assertDoesNotThrow(() -> kafkaOutboxEventSender.send(event));
    }
}
