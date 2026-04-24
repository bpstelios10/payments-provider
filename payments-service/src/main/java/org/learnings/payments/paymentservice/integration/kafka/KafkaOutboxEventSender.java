package org.learnings.payments.paymentservice.integration.kafka;

import org.learnings.payments.paymentservice.infrastructure.outbox.OutboxEvent;
import org.learnings.payments.paymentservice.infrastructure.outbox.OutboxEventSender;
import org.springframework.stereotype.Component;

@Component
public class KafkaOutboxEventSender implements OutboxEventSender {

    @Override
    public void send(OutboxEvent event) {
    }
}
