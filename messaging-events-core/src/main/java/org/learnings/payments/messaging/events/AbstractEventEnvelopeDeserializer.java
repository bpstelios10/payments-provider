package org.learnings.payments.messaging.events;

import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.TimeZone;

/**
 * Abstract Kafka {@link Deserializer} for {@link EventEnvelope}.
 *
 * <p>Contains the shared deserialization logic: reads {@code eventType} from the
 * raw JSON, resolves the payload class from the subclass-provided
 * {@link #typeRegistry()}, and deserializes into a typed {@code EventEnvelope<T>}.
 *
 * <p>Subclasses must implement {@link #typeRegistry()} declaring the event types
 * they handle. Kafka instantiates deserializers via reflection — ensure subclasses
 * have a no-arg constructor.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * public class PaymentEnvelopeDeserializer extends AbstractEventEnvelopeDeserializer {
 *     protected Map<String, Class<?>> typeRegistry() {
 *         return Map.of(EventType.PAYMENT_CAPTURED.name(), PaymentEventPayload.class);
 *     }
 * }
 * }</pre>
 */
public abstract class AbstractEventEnvelopeDeserializer implements Deserializer<EventEnvelope<?>> {

    private static final Logger log = LoggerFactory.getLogger(AbstractEventEnvelopeDeserializer.class);

    private final JsonMapper mapper = JsonMapper.builder()
            .defaultTimeZone(TimeZone.getTimeZone("UTC"))
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .enable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /**
     * Returns the mapping from event type name to payload class.
     *
     * <p>The key must match the {@code eventType} field value in the JSON
     * (typically {@code YourEventType.MY_TYPE.name()}).
     *
     * @return map of event type name → payload class; must not be {@code null}
     */
    protected abstract Map<String, Class<?>> typeRegistry();

    /**
     * Deserializes raw Kafka message bytes into a typed {@link EventEnvelope}.
     *
     * @param topic the Kafka topic (used for error logging)
     * @param data  the raw bytes; returns {@code null} if {@code null} or empty
     * @return the deserialized envelope
     * @throws RuntimeException if deserialization fails or the event type is not registered
     */
    @Override
    public EventEnvelope<?> deserialize(String topic, byte[] data) {
        if (data == null || data.length < 1) return null;

        try {
            JsonNode root = mapper.readTree(data);
            String eventType = root.get("eventType").asString();
            Class<?> payloadClass = typeRegistry().get(eventType);
            if (payloadClass == null) {
                throw new IllegalArgumentException("No payload class registered for event type: " + eventType);
            }
            JavaType type = mapper.getTypeFactory().constructParametricType(EventEnvelope.class, payloadClass);
            return mapper.readValue(data, type);
        } catch (Exception e) {
            log.error("Error while deserializing event envelope with topic [{}]", topic);
            throw new RuntimeException("Error deserializing EventEnvelope", e);
        }
    }
}
