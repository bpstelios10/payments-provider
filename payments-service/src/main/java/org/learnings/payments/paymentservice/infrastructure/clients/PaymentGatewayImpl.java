package org.learnings.payments.paymentservice.infrastructure.clients;

import org.learnings.payments.paymentservice.application.PaymentGateway;
import org.learnings.payments.paymentservice.application.dtos.PaymentDto;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentGatewayImpl implements PaymentGateway {
    @Override
    public void executePayment(PaymentDto paymentDto, UUID idempotencyKey) {

    }
}
