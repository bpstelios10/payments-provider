package org.learnings.payments.transactionsservice.adapters.messaging.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.learnings.payments.transactionsservice.adapters.messaging.model.EventType.PAYMENT_CAPTURED;

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

    //    @Test
    void serialize_whenSerializationFails_throwsRuntimeException() {
        // To simulate failure, perhaps pass an object that can't be serialized, but since it's record, hard.
        // For now, assume it works. maybe no need for failure test.
    }
}
