package org.learnings.payments.paymentservice.application;

public interface EventMessagePublisher {

    void publish(EventMessage event);
}
