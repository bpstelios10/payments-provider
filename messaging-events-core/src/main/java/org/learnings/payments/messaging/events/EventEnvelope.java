package org.learnings.payments.messaging.events;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic envelope wrapping any event published on the messaging platform.
 *
 * <p>The library owns the {@link EventType} enum — all event types across the
 * platform are defined there. Adding a new event type means adding it to the enum
 * and registering the payload class in the consuming service's deserializer.
 *
 * @param <T> the type of the event payload
 */
public record EventEnvelope<T>(
        UUID eventId,
        EventType eventType,
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@type")
        T payload,
        Instant occurredAt
) {}
