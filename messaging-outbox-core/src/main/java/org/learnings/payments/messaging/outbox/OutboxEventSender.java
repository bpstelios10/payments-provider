package org.learnings.payments.messaging.outbox;

/**
 * Delivers a single outbox record to an external messaging system.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Serializing the record payload into the target message format</li>
 *   <li>Routing to the correct topic, queue or exchange</li>
 *   <li>Delivery semantics (sync vs async, at-least-once, etc.)</li>
 * </ul>
 *
 * <p>Implementations must throw an exception on failure so that
 * {@link OutboxProcessor} can schedule a retry.
 *
 * <p>This interface has no framework dependencies and can be implemented
 * for any messaging technology (Kafka, ActiveMQ, RabbitMQ, etc.).
 */
public interface OutboxEventSender {

    /**
     * Sends the given outbox record to the external messaging system.
     *
     * @param record the outbox record to send; never {@code null}
     * @throws Exception if sending fails — the processor will schedule a retry
     */
    void send(OutboxRecord record);
}
