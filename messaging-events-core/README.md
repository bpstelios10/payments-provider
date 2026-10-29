# messaging-events-core

Shared event envelope and Kafka (de)serializers for services on the platform.

## Contents
| Type | Role |
|---|---|
| `EventEnvelope<T>` | `{eventId, eventType, payload, occurredAt}`. `eventId` is the consumer's idempotency key. |
| `EventType` | Platform-wide event type names (currently `PAYMENT_CAPTURED`) |
| `EventEnvelopeSerializer` | Kafka `Serializer`: JSON, UTC, ISO-8601 instants |
| `AbstractEventEnvelopeDeserializer` | Kafka `Deserializer`: reads `eventType` and resolves the payload class from a subclass-provided registry |

## Consumer usage
```java
public class MyDeserializer extends AbstractEventEnvelopeDeserializer {
    @Override protected Map<String, Class<?>> typeRegistry() {
        return Map.of(EventType.PAYMENT_CAPTURED.name(), PaymentEventPayload.class);
    }
}
```
Register it as `value.deserializer`. Consumers own their payload classes and register only the types they handle (tolerant reader).

## Adding an event type
1. Add it to `EventType`.
2. The producer emits the envelope with that type.
3. Each interested consumer registers a payload class.

Evolve payloads additively only. Never remove or rename fields without a new version.
