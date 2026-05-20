package org.learnings.payments.messaging.events;

import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.TimeZone;

/**
 * Kafka {@link Serializer} for {@link EventEnvelope}.
 *
 * <p>Serializes any {@code EventEnvelope} to JSON bytes using UTC timestamps
 * with nanosecond precision. Register as {@code value.serializer} in the Kafka
 * producer configuration.
 */
public class EventEnvelopeSerializer implements Serializer<EventEnvelope<?>> {

    private final JsonMapper mapper = JsonMapper.builder()
            .defaultTimeZone(TimeZone.getTimeZone("UTC"))
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .enable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /**
     * Serializes the given {@link EventEnvelope} to JSON bytes.
     *
     * @param topic the Kafka topic (unused but required by the interface)
     * @param data  the envelope to serialize
     * @return JSON bytes, or {@code null} if {@code data} is {@code null}
     * @throws RuntimeException if serialization fails
     */
    @Override
    public byte[] serialize(String topic, EventEnvelope<?> data) {
        if (data == null) return null;

        try {
            return mapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing EventEnvelope", e);
        }
    }
}
