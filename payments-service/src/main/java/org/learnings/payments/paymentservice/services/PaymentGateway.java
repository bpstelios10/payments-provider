package org.learnings.payments.paymentservice.services;

public interface PaymentGateway {

    void executePayment(PaymentDto paymentDto);
}
