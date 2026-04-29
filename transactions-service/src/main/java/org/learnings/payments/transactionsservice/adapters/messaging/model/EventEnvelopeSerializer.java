package org.learnings.payments.transactionsservice.adapters.messaging.model;

import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.TimeZone;

public class EventEnvelopeSerializer implements Serializer<EventEnvelope<?>> {

    private final JsonMapper mapper = JsonMapper.builder()
            .defaultTimeZone(TimeZone.getTimeZone("UTC"))
            // Prevent conversion to local JVM time
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            // Ensure full 9-digit nanosecond precision
            .enable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

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
