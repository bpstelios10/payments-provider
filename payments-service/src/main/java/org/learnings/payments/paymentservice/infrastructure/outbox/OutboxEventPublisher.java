package org.learnings.payments.paymentservice.infrastructure.outbox;

import org.learnings.payments.messaging.outbox.OutboxRecord;
import org.learnings.payments.messaging.outbox.jpa.OutboxEvent;
import org.learnings.payments.messaging.outbox.jpa.OutboxEventRepository;
import org.learnings.payments.paymentservice.application.EventMessage;
import org.learnings.payments.paymentservice.application.EventMessagePublisher;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxEventPublisher implements EventMessagePublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(EventMessage event) {
        String stringPayload = objectMapper.writeValueAsString(event.payload());
        OutboxRecord record = new OutboxRecord(event.aggregateId(), event.aggregateType(), event.eventType(), stringPayload);

        outboxEventRepository.save(OutboxEvent.fromRecord(record));
    }
}
