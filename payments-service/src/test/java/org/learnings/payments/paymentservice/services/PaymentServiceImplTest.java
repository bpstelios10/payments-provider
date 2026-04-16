package org.learnings.payments.paymentservice.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.repositories.PaymentRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    void createPayment_succeeds() {
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", UUID.randomUUID());
        Payment payment = PaymentDto.toPayment(dto, "pending");
        Payment savedPayment = mock(Payment.class);
        when(paymentRepository.save(payment)).thenReturn(savedPayment);
        when(savedPayment.getPaymentId()).thenReturn(1L);
        when(savedPayment.getStatus()).thenReturn("pending");
        when(savedPayment.getCreatedDate()).thenReturn(Instant.now());

        PaymentResponseDto resultPaymentId = paymentService.createPayment(dto);

        assertThat(1L).isEqualTo(resultPaymentId.paymentId());
        assertThat("pending").isEqualTo(resultPaymentId.status());
    }

    @Test
    void createPayment_whenDataAccessExceptionAndNotDataIntegrityViolationException_throwsTheException() {
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", UUID.randomUUID());
        Payment payment = PaymentDto.toPayment(dto, "pending");
        DataAccessException dataAccessException = mock(DataAccessException.class);
        when(paymentRepository.save(payment)).thenThrow(dataAccessException);

        assertThatThrownBy(() -> paymentService.createPayment(dto))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void createPayment_whenDataIntegrityViolationExceptionAndNotUniqueConstraintError_throwsTheException() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", idempotencyKey);
        Payment payment = PaymentDto.toPayment(dto, "pending");
        DataIntegrityViolationException dataIntegrityViolationException = new DataIntegrityViolationException("some cause");
        when(paymentRepository.save(payment)).thenThrow(dataIntegrityViolationException);

        assertThatThrownBy(() -> paymentService.createPayment(dto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("some cause");
    }

    @Test
    void createPayment_whenUniqueConstraintErrorButDifferentIdempotencyKey_throwsTheException() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", idempotencyKey);
        Payment payment = PaymentDto.toPayment(dto, "pending");
        DataIntegrityViolationException dataIntegrityViolationException =
                new DataIntegrityViolationException("cause: UNIQUE_IDEMTOTENCY_KEY");
        when(paymentRepository.save(payment)).thenThrow(dataIntegrityViolationException);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(dto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("cause: UNIQUE_IDEMTOTENCY_KEY");
    }

    @Test
    void createPayment_whenUniqueConstraintErrorAndSameIdempotencyKey_returnsExistingPaymentId() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", idempotencyKey);
        Payment payment = PaymentDto.toPayment(dto, "pending");
        DataIntegrityViolationException dataIntegrityViolationException =
                new DataIntegrityViolationException("cause: UNIQUE_IDEMTOTENCY_KEY");
        when(paymentRepository.save(payment)).thenThrow(dataIntegrityViolationException);
        Payment existingPayment = mock(Payment.class);
        when(existingPayment.getPaymentId()).thenReturn(1L);
        when(existingPayment.getStatus()).thenReturn("pending");
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        PaymentResponseDto resultPaymentId = paymentService.createPayment(dto);

        assertThat(1L).isEqualTo(resultPaymentId.paymentId());
        assertThat("pending").isEqualTo(resultPaymentId.status());
    }
}
