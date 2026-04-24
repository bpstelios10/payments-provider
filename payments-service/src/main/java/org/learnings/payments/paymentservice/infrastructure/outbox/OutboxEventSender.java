package org.learnings.payments.paymentservice.infrastructure.outbox;

public interface OutboxEventSender {

    /**
     * Sends a single outbox event to the external messaging system.
     * <p>
     * Implementations are responsible for:
     * - serialization
     * - routing (e.g. topic selection)
     * - delivery semantics (sync/async)
     * <p>
     * Should throw an exception if sending fails,
     * so the outbox processor can retry.
     */
    void send(OutboxEvent event);
}
