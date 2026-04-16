package org.learnings.payments.paymentservice.web.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.learnings.payments.paymentservice.services.PaymentDto;
import org.learnings.payments.paymentservice.services.PaymentResponseDto;
import org.learnings.payments.paymentservice.services.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("payments")
public class PaymentsController {

    private final PaymentService paymentService;

    public PaymentsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponseDto> createPayment(@Valid @NotNull @RequestBody CreatePayment requestBody) {
        PaymentDto paymentDto = CreatePayment.toPaymentDto(requestBody);

        return ResponseEntity.ok(paymentService.createPayment(paymentDto));
    }

//    @PostMapping("/execute")
//    public ResponseEntity<PaymentResponseDto> executePayment(@NotEmpty String paymentId) {
//
//        return ResponseEntity.ok(paymentService.createPayment(paymentDto).toString());
//    }

    public record CreatePayment(
            @NotNull
            @DecimalMin(value = "0.00", inclusive = false)
            @Digits(integer = 17, fraction = 2)
            BigDecimal amount,
            String currency,
            @NotEmpty
            String merchantId,
            @NotNull
            UUID idempotencyKey) {
        static PaymentDto toPaymentDto(CreatePayment requestBody) {
            // TODO should be checking not empty, not just null
            String currency = requestBody.currency == null ? "USD" : requestBody.currency;
            return new PaymentDto(requestBody.amount, currency, requestBody.merchantId, requestBody.idempotencyKey);
        }
    }
}
