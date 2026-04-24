package org.learnings.payments.paymentservice.services.ports;

public record EventMessage(Long aggregateId, String aggregateType, String eventType, String payload) {
}
