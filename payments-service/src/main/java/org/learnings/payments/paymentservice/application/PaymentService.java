package org.learnings.payments.paymentservice.application;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.learnings.payments.paymentservice.application.dtos.PaymentDto;

public interface PaymentService {

    PaymentDto createPayment(@NotNull PaymentDto paymentDto);

    PaymentDto executePayment(@NotEmpty long paymentId);
}
