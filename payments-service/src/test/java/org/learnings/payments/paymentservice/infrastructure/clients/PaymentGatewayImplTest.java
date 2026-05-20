package org.learnings.payments.paymentservice.infrastructure.clients;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentGatewayImplTest {

    private final PaymentGatewayImpl paymentGateway = new PaymentGatewayImpl();

    @Test
    void executePayment() {
        paymentGateway.executePayment(null, null);
    }
}
