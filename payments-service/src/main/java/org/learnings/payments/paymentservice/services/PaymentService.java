package org.learnings.payments.paymentservice.services;

public interface PaymentService {

    PaymentResponseDto createPayment(PaymentDto paymentDto);
}
