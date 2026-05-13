package org.learnings.payments.paymentservice.application;

public record EventMessage(Long aggregateId, String aggregateType, String eventType, String payload) {
}
