package org.learnings.payments.paymentservice.application;

import org.learnings.payments.paymentservice.application.dtos.PaymentDto;

import java.util.UUID;

public interface PaymentGateway {

    void executePayment(PaymentDto paymentDto, UUID idempotencyKey);
}
