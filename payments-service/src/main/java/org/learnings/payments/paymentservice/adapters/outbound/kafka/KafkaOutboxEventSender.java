package org.learnings.payments.paymentservice.adapters.outbound.kafka;

import org.learnings.payments.messaging.outbox.OutboxEventSender;
import org.learnings.payments.messaging.outbox.OutboxRecord;
import org.springframework.stereotype.Component;

@Component
public class KafkaOutboxEventSender implements OutboxEventSender {

    @Override
    public void send(OutboxRecord record) {
        System.out.println("Inside Kafka for event: " + record.getEventType());
    }
}
