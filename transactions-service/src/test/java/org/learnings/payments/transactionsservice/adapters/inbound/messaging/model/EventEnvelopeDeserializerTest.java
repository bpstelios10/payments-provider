package org.learnings.payments.transactionsservice.adapters.inbound.messaging.model;

import org.junit.jupiter.api.Test;
import org.learnings.payments.messaging.events.EventEnvelope;
import org.learnings.payments.messaging.events.EventType;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeDeserializerTest {

    private final EventEnvelopeDeserializer deserializer = new EventEnvelopeDeserializer();

    @Test
    void deserialize_whenValidPaymentCapturedEvent_returnsTypedPayload() {
        String json = """
                {
                    "eventId": "123e4567-e89b-12d3-a456-426614174000",
                    "eventType": "PAYMENT_CAPTURED",
                    "payload": {
                        "@type": "org.learnings.payments.transactionsservice.adapters.inbound.messaging.model.PaymentEventPayload",
                        "paymentId": 123,
                        "amount": 100.00,
                        "currency": "USD",
                        "occurredAt": "2023-01-01T00:00:00Z"
                    },
                    "occurredAt": "2023-01-01T00:00:00Z"
                }
                """;

        EventEnvelope<?> result = deserializer.deserialize("topic", json.getBytes());

        assertThat(result).isNotNull();
        assertThat(result.eventType()).isEqualTo(EventType.PAYMENT_CAPTURED);
        assertThat(result.payload()).isInstanceOf(PaymentEventPayload.class);
        PaymentEventPayload payload = (PaymentEventPayload) result.payload();
        assertThat(payload.paymentId()).isEqualTo(123L);
        assertThat(payload.currency()).isEqualTo("USD");
    }
}
