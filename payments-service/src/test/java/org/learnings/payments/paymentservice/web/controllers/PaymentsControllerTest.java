package org.learnings.payments.paymentservice.web.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnings.payments.paymentservice.services.PaymentDto;
import org.learnings.payments.paymentservice.services.PaymentResponseDto;
import org.learnings.payments.paymentservice.services.PaymentService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        PaymentDto paymentDto = new PaymentDto(BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId);
        PaymentResponseDto savedPayment = new PaymentResponseDto(1L, "pending");
        when(paymentService.createPayment(paymentDto)).thenReturn(savedPayment);

        ResponseEntity<PaymentResponseDto> response = paymentsController.createPayment(requestBody);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().paymentId()).isEqualTo(1L);
        assertThat(response.getBody().status()).isEqualTo("pending");
    }

    @Test
    void createPayment_whenNoCurrency_usesDefaultCurrency() {
        UUID idempotencyId = UUID.randomUUID();
        PaymentsController.CreatePayment requestBody =
                new PaymentsController.CreatePayment(BigDecimal.valueOf(10.2), null, "merch-1", idempotencyId);
        PaymentDto paymentDto = new PaymentDto(BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId);
        PaymentResponseDto savedPayment = new PaymentResponseDto(1L, "pending");
        when(paymentService.createPayment(paymentDto)).thenReturn(savedPayment);

        ResponseEntity<PaymentResponseDto> response = paymentsController.createPayment(requestBody);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().paymentId()).isEqualTo(1L);
        assertThat(response.getBody().status()).isEqualTo("pending");
    }
}
