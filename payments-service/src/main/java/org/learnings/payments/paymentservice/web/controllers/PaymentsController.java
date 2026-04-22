package org.learnings.payments.paymentservice.web.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.services.PaymentService;
import org.learnings.payments.paymentservice.services.dtos.PaymentDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

import static org.learnings.payments.paymentservice.web.controllers.PaymentsController.PaymentResponse.fromPaymentDto;

@RestController
@RequestMapping("payments")
public class PaymentsController {

    private final PaymentService paymentService;

    public PaymentsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @NotNull @RequestBody CreatePayment requestBody) {
        PaymentDto paymentDto = CreatePayment.toPaymentDto(requestBody);

        PaymentDto responseDto = paymentService.createPayment(paymentDto);

        return ResponseEntity.ok(fromPaymentDto(responseDto));
    }

    @PostMapping("/{paymentId}/execute")
    public ResponseEntity<PaymentResponse> executePayment(@PathVariable Long paymentId) {
        PaymentDto responseDto = paymentService.executePayment(paymentId);

        return ResponseEntity.ok(fromPaymentDto(responseDto));
    }

    @SuppressWarnings({"UnusedDeclaration"})
    @ResponseStatus(value= HttpStatus.CONFLICT, reason="Race condition error")
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public void conflict() { }

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
            return new PaymentDto(requestBody.amount, currency, requestBody.merchantId, requestBody.idempotencyKey, null);
        }
    }

    public record PaymentResponse(Long paymentId, PaymentStatus status) {
        static PaymentResponse fromPaymentDto(PaymentDto paymentDto) {
            return new PaymentResponse(paymentDto.getPaymentId(), paymentDto.getStatus());
        }
    }
}
