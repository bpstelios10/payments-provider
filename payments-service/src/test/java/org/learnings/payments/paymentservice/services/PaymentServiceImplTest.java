package org.learnings.payments.paymentservice.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.repositories.PaymentRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(savedPayment.getCreatedDate()).thenReturn(Instant.now());

        Long resultPaymentId = paymentService.createPayment(dto);

        assertThat(1L).isEqualTo(resultPaymentId);
    }
}
