package org.learnings.payments.paymentservice.services.ports;

public interface EventMessagePublisher {

    void publish(EventMessage event);
}
