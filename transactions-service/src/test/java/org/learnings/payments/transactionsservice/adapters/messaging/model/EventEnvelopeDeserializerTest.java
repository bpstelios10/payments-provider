package org.learnings.payments.transactionsservice.adapters.messaging.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEnvelopeDeserializerTest {

    private final EventEnvelopeDeserializer deserializer = new EventEnvelopeDeserializer();

    @Test
    void deserialize_whenValidData_returnsEventEnvelope() {
        String json = """
                {
                    "eventId": "123e4567-e89b-12d3-a456-426614174000",
                    "eventType": "PAYMENT_CAPTURED",
                    "payload": {
                        "@type": "org.learnings.payments.transactionsservice.adapters.messaging.model.PaymentEventPayload",
                        "paymentId": 123,
                        "amount": 100.00,
                        "currency": "USD",
                        "occurredAt": "2023-01-01T00:00:00Z"
                    },
                    "occurredAt": "2023-01-01T00:00:00Z"
                }
                """;
        byte[] data = json.getBytes();

        EventEnvelope<?> result = deserializer.deserialize("topic", data);

        assertThat(result).isNotNull();
        assertThat(result.eventType()).isEqualTo(EventType.PAYMENT_CAPTURED);
        assertThat(result.payload()).isInstanceOf(PaymentEventPayload.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void deserialize_whenDataIsNullOrEmpty_returnsNull(byte[] data) {
        EventEnvelope<?> result = deserializer.deserialize("topic", data);

        assertThat(result).isNull();
    }

    @Test
    void deserialize_whenInvalidJson_throwsRuntimeException() {
        byte[] data = "invalid json".getBytes();

        assertThatThrownBy(() -> deserializer.deserialize("topic", data))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error deserializing EventEnvelope");
    }

    @Test
    void resolve_whenUnknownType_throwsIllegalArgumentException() {
        String json = """
                {
                    "eventId": "123e4567-e89b-12d3-a456-426614174000",
                    "eventType": "INVALID_PAYMENT",
                    "payload": {
                        "@type": "org.learnings.payments.transactionsservice.adapters.messaging.model.PaymentEventPayload",
                        "paymentId": 123,
                        "amount": 100.00,
                        "currency": "USD",
                        "occurredAt": "2023-01-01T00:00:00Z"
                    },
                    "occurredAt": "2023-01-01T00:00:00Z"
                }
                """;
        byte[] data = json.getBytes();

        assertThatThrownBy(() -> deserializer.deserialize("topic", data))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error deserializing EventEnvelope")
                .hasRootCauseMessage("No enum constant org.learnings.payments.transactionsservice.adapters.messaging.model.EventType.INVALID_PAYMENT");
    }
}