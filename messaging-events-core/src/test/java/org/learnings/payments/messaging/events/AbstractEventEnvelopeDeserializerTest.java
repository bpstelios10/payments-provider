package org.learnings.payments.messaging.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractEventEnvelopeDeserializerTest {

    /** Minimal concrete subclass for testing — uses a simple TestPayload. */
    private final AbstractEventEnvelopeDeserializer deserializer = new AbstractEventEnvelopeDeserializer() {
        @Override
        protected Map<String, Class<?>> typeRegistry() {
            return Map.of(EventType.PAYMENT_CAPTURED.name(), TestPayload.class);
        }
    };

    @Test
    void deserialize_whenValidData_returnsEventEnvelope() {
        String json = """
                {
                    "eventId": "123e4567-e89b-12d3-a456-426614174000",
                    "eventType": "PAYMENT_CAPTURED",
                    "payload": {
                        "@type": "%s",
                        "amount": 100.00
                    },
                    "occurredAt": "2023-01-01T00:00:00Z"
                }
                """.formatted(TestPayload.class.getName());

        EventEnvelope<?> result = deserializer.deserialize("topic", json.getBytes());

        assertThat(result).isNotNull();
        assertThat(result.eventType()).isEqualTo(EventType.PAYMENT_CAPTURED);
        assertThat(result.payload()).isInstanceOf(TestPayload.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void deserialize_whenDataIsNullOrEmpty_returnsNull(byte[] data) {
        assertThat(deserializer.deserialize("topic", data)).isNull();
    }

    @Test
    void deserialize_whenInvalidJson_throwsRuntimeException() {
        assertThatThrownBy(() -> deserializer.deserialize("topic", "invalid json".getBytes()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error deserializing EventEnvelope");
    }

    @Test
    void deserialize_whenUnregisteredEventType_throwsRuntimeException() {
        String json = """
                {
                    "eventId": "123e4567-e89b-12d3-a456-426614174000",
                    "eventType": "UNKNOWN_TYPE",
                    "payload": {},
                    "occurredAt": "2023-01-01T00:00:00Z"
                }
                """;

        assertThatThrownBy(() -> deserializer.deserialize("topic", json.getBytes()))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error deserializing EventEnvelope")
                .hasRootCauseMessage("No payload class registered for event type: UNKNOWN_TYPE");
    }

    record TestPayload(BigDecimal amount) {}
}
