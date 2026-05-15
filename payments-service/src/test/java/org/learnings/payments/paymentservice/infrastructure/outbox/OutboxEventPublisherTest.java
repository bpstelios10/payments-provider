package org.learnings.payments.paymentservice.infrastructure.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnings.payments.messaging.outbox.jpa.OutboxEventRepository;
import org.learnings.payments.paymentservice.application.EventMessage;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ObjectMapper objectMapper;
    @InjectMocks
    private OutboxEventPublisher outboxEventPublisher;

    @Test
    void publish() {
        EventMessage event = new EventMessage(1L, "PAYMENT", "INITIATED", new Object());
        String stringPayload = "{\"key\":\"value\"}";
        when(objectMapper.writeValueAsString(event.payload())).thenReturn(stringPayload);

        outboxEventPublisher.publish(event);

        verify(outboxEventRepository).save(argThat(outboxEvent ->
                outboxEvent.getAggregateId() == 1L &&
                        outboxEvent.getAggregateType().equals("PAYMENT") &&
                        outboxEvent.getEventType().equals("INITIATED") &&
                        outboxEvent.getPayload().equals(stringPayload)
        ));
    }
}
