package org.learnings.payments.messaging.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.learnings.payments.messaging.events.EventType.PAYMENT_CAPTURED;

class EventEnvelopeSerializerTest {

    private final EventEnvelopeSerializer serializer = new EventEnvelopeSerializer();

    @Test
    void serialize_whenValidData_returnsSerializedBytes() {
        EventEnvelope<TestPayload> envelope =
                new EventEnvelope<>(UUID.randomUUID(), PAYMENT_CAPTURED, new TestPayload("val"), Instant.now());

        byte[] result = serializer.serialize("topic", envelope);

        assertThat(result).isNotNull();
        assertThat(new String(result)).contains("PAYMENT_CAPTURED");
    }

    @Test
    void serialize_whenDataIsNull_returnsNull() {
        byte[] result = serializer.serialize("topic", null);

        assertThat(result).isNull();
    }

    @Test
    void serialize_whenSerializationFails_throwsRuntimeException() {
        EventEnvelope<Object> envelope = new EventEnvelope<>(UUID.randomUUID(), PAYMENT_CAPTURED,
                new UnserializablePayload(), Instant.now());

        assertThatThrownBy(() -> serializer.serialize("topic", envelope))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error serializing EventEnvelope");
    }

    record TestPayload(String value) {}

    static class UnserializablePayload {
        public String getValue() {
            throw new RuntimeException("cannot serialize");
        }
    }
}
