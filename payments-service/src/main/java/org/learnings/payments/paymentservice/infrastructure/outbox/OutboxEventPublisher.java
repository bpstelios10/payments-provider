package org.learnings.payments.paymentservice.infrastructure.outbox;

import org.learnings.payments.paymentservice.services.ports.EventMessage;
import org.learnings.payments.paymentservice.services.ports.EventMessagePublisher;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventPublisher implements EventMessagePublisher {

    private final OutboxRepository outboxRepository;

    public OutboxEventPublisher(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void publish(EventMessage event) {
        outboxRepository.save(OutboxEvent.fromEventMessage(event));
    }
}
