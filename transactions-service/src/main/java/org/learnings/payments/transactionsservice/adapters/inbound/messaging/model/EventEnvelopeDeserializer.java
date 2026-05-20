package org.learnings.payments.transactionsservice.adapters.inbound.messaging.model;

import org.learnings.payments.messaging.events.AbstractEventEnvelopeDeserializer;
import org.learnings.payments.messaging.events.EventType;

import java.util.Map;

/**
 * Kafka deserializer for payment events consumed by transactions-service.
 *
 * <p>Extends {@link AbstractEventEnvelopeDeserializer} and registers the
 * event types this service handles.
 */
public class EventEnvelopeDeserializer extends AbstractEventEnvelopeDeserializer {

    @Override
    protected Map<String, Class<?>> typeRegistry() {
        return Map.of(EventType.PAYMENT_CAPTURED.name(), PaymentEventPayload.class);
    }
}
