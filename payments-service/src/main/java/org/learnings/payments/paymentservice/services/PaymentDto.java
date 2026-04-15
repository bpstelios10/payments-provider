package org.learnings.payments.paymentservice.services;

import org.learnings.payments.paymentservice.domain.Payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentDto(BigDecimal amount, String currency, String merchantId, UUID idempotencyKey) {

    static Payment toPayment(PaymentDto paymentDto, String status) {
        return new Payment(paymentDto.amount, paymentDto.currency, paymentDto.merchantId, paymentDto.idempotencyKey, status);
    }
}
