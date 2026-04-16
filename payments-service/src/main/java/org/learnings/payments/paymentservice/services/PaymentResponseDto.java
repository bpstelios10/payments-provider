package org.learnings.payments.paymentservice.services;

import org.learnings.payments.paymentservice.domain.Payment;

public record PaymentResponseDto(Long paymentId, String status) {
    static PaymentResponseDto fromPayment(Payment payment) {
        return new PaymentResponseDto(payment.getPaymentId(), payment.getStatus());
    }
}
