package org.learnings.payments.paymentservice.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.services.PaymentService;
import org.learnings.payments.paymentservice.services.dtos.PaymentDto;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.learnings.payments.paymentservice.web.controllers.PaymentsController.CreatePayment.toPaymentDto;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentsControllerTest {

    @Mock
    private PaymentService paymentService;
    @InjectMocks
    private PaymentsController paymentsController;

    @Test
    void createPayment_succeeds() {
        UUID idempotencyId = UUID.randomUUID();
        PaymentsController.CreatePayment requestBody =
                new PaymentsController.CreatePayment(BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId);
        PaymentDto paymentDto = new PaymentDto(1L, BigDecimal.valueOf(10.2), "USD",
                "merch-1", UUID.randomUUID(), PaymentStatus.INITIATED, Instant.now(), Instant.now());
        when(paymentService.createPayment(toPaymentDto(requestBody))).thenReturn(paymentDto);

        ResponseEntity<PaymentsController.PaymentResponse> response = paymentsController.createPayment(requestBody);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().paymentId()).isEqualTo(paymentDto.getPaymentId());
        assertThat(response.getBody().status()).isEqualTo(paymentDto.getStatus());
    }

    @Test
    void createPayment_whenNoCurrency_usesDefaultCurrency() {
        UUID idempotencyId = UUID.randomUUID();
        PaymentsController.CreatePayment requestBody =
                new PaymentsController.CreatePayment(BigDecimal.valueOf(10.2), null, "merch-1", idempotencyId);
        PaymentDto paymentDto = new PaymentDto(1L, BigDecimal.valueOf(10.2), "USD",
                "merch-1", UUID.randomUUID(), PaymentStatus.INITIATED, Instant.now(), Instant.now());
        when(paymentService.createPayment(toPaymentDto(requestBody))).thenReturn(paymentDto);

        ResponseEntity<PaymentsController.PaymentResponse> response = paymentsController.createPayment(requestBody);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().paymentId()).isEqualTo(paymentDto.getPaymentId());
        assertThat(response.getBody().status()).isEqualTo(paymentDto.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"CAPTURED", "FAILED"})
    void executePayment_succeeds(PaymentStatus statuses) {
        PaymentDto paymentDto = new PaymentDto(1L, BigDecimal.valueOf(10.2), "USD",
                "merch-1", UUID.randomUUID(), statuses, Instant.now(), Instant.now());
        when(paymentService.executePayment(paymentDto.getPaymentId())).thenReturn(paymentDto);

        ResponseEntity<PaymentsController.PaymentResponse> response = paymentsController.executePayment(paymentDto.getPaymentId());

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().paymentId()).isEqualTo(paymentDto.getPaymentId());
        assertThat(response.getBody().status()).isEqualTo(statuses);
    }
}
