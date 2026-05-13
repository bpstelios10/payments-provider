package org.learnings.payments.paymentservice.application;

import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.springframework.transaction.support.TransactionCallback;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public final class PaymentUseCaseTestUtils {
    private PaymentUseCaseTestUtils() {}

    static Payment getMockedPayment(long paymentId) {
        Payment mockedPayment = mock(Payment.class);
        when(mockedPayment.getPaymentId()).thenReturn(paymentId);

        return mockedPayment;
    }

    static Payment getMockedPayment(long paymentId, UUID idempotencyKey) {
        Payment mockedPayment = getMockedPayment(paymentId);
        when(mockedPayment.getIdempotencyKey()).thenReturn(idempotencyKey);

        return mockedPayment;
    }

    @SuppressWarnings("SameParameterValue")
    static Payment getMockedPayment(long paymentId, UUID idempotencyKey, PaymentStatus paymentStatus) {
        Payment mockedPayment = getMockedPayment(paymentId, idempotencyKey);
        when(mockedPayment.getStatus()).thenReturn(paymentStatus);

        return mockedPayment;
    }

    @SuppressWarnings("SameParameterValue")
    static Payment getMockedPayment(long paymentId, UUID idempotencyKey, PaymentStatus paymentStatus, PaymentStatus paymentStatus2) {
        Payment mockedPayment = getMockedPayment(paymentId, idempotencyKey);
        when(mockedPayment.getStatus()).thenReturn(paymentStatus).thenReturn(paymentStatus2);

        return mockedPayment;
    }
}
