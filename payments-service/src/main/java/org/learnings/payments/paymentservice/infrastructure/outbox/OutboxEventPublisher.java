package org.learnings.payments.paymentservice.infrastructure.outbox;

import org.learnings.payments.paymentservice.application.EventMessage;
import org.learnings.payments.paymentservice.application.EventMessagePublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxEventPublisher implements EventMessagePublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(EventMessage event) {
        String stringPayload = objectMapper.writeValueAsString(event.payload());
        outboxRepository.save(OutboxEvent.fromEventMessage(event, stringPayload));
    }
}
