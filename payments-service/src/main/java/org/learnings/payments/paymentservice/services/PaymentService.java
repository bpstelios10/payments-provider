package org.learnings.payments.paymentservice.services;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public interface PaymentService {

    PaymentResponseDto createPayment(@NotNull PaymentDto paymentDto);

    PaymentResponseDto executePayment(@NotEmpty long paymentId);
}
