package org.learnings.payments.paymentservice.services;

import org.learnings.payments.paymentservice.services.dtos.PaymentDto;

import java.util.UUID;

public interface PaymentGateway {

    void executePayment(PaymentDto paymentDto, UUID idempotencyKey);
}
