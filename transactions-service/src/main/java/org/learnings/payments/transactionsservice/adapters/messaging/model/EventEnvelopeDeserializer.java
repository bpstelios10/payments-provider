package org.learnings.payments.transactionsservice.adapters.messaging.model;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.TimeZone;

@Slf4j
public class EventEnvelopeDeserializer implements Deserializer<EventEnvelope<?>> {

    private static final Map<EventType, Class<?>> TYPES = Map.of(
            EventType.PAYMENT_CAPTURED, PaymentEventPayload.class
    );
    private final JsonMapper mapper = JsonMapper.builder()
            .defaultTimeZone(TimeZone.getTimeZone("UTC"))
            // Prevent conversion to local JVM time
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            // Ensure full 9-digit nanosecond precision
            .enable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Override
    public EventEnvelope<?> deserialize(String topic, byte[] data) {
        if (data == null) return null;

        try {
            // read tree to inspect eventType
            JsonNode root = mapper.readTree(data);
            EventType eventType = EventType.valueOf(root.get("eventType").asString());
            // resolve payload class
            Class<?> payloadClass = resolve(eventType);
            // build typed JavaType
            JavaType type = mapper.getTypeFactory().constructParametricType(EventEnvelope.class, payloadClass);

            return mapper.readValue(data, type);
        } catch (Exception e) {
            log.error("Error while deserializing event envelope with topic [{}]", topic);
            throw new RuntimeException("Error deserializing EventEnvelope", e);
        }
    }

    public static Class<?> resolve(EventType type) {
        Class<?> clazz = TYPES.get(type);

        if (clazz == null) {
            throw new IllegalArgumentException("No payload mapping for: " + type);
        }

        return clazz;
    }
}
