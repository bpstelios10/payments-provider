package org.learnings.payments.transactionsservice.adapters.inbound.messaging.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.learnings.payments.transactionsservice.adapters.inbound.messaging.model.EventType.PAYMENT_CAPTURED;

class EventEnvelopeSerializerTest {

    private final EventEnvelopeSerializer serializer = new EventEnvelopeSerializer();

    @Test
    void serialize_whenValidData_returnsSerializedBytes() {
        Instant paymentOccurredAt = Instant.now();
        PaymentEventPayload payload =
                new PaymentEventPayload(123L, BigDecimal.valueOf(100.00), "USD", paymentOccurredAt);
        EventEnvelope<PaymentEventPayload> envelope =
                new EventEnvelope<>(UUID.randomUUID(), PAYMENT_CAPTURED, payload, paymentOccurredAt.plusSeconds(1));

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

    static class UnserializablePayload {
        public String getValue() {
            throw new RuntimeException("cannot serialize");
        }
    }
}
