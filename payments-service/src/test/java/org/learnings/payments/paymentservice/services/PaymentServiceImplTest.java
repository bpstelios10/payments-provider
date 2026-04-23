package org.learnings.payments.paymentservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;
import org.learnings.payments.paymentservice.repositories.PaymentRepository;
import org.learnings.payments.paymentservice.services.dtos.PaymentDto;
import org.learnings.payments.paymentservice.services.statustransitions.PaymentActionStrategy;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.learnings.payments.paymentservice.domain.Payment.UNIQUE_PAYMENT_IDEMPOTENCY_KEY;
import static org.learnings.payments.paymentservice.domain.PaymentStatus.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private PaymentActionStrategy paymentActionStrategy;
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setup() {
        paymentService = new PaymentServiceImpl(paymentRepository, paymentGateway, List.of(paymentActionStrategy));
    }

    @Test
    void createPayment_succeeds() {
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", UUID.randomUUID(), null);
        Payment payment = PaymentDto.toPayment(dto, INITIATED);
        Payment savedPayment = mock(Payment.class);
        when(paymentRepository.save(payment)).thenReturn(savedPayment);
        when(savedPayment.getPaymentId()).thenReturn(1L);
        when(savedPayment.getStatus()).thenReturn(INITIATED);
        when(savedPayment.getCreatedDate()).thenReturn(Instant.now());

        PaymentDto createdPaymentDto = paymentService.createPayment(dto);

        assertThat(1L).isEqualTo(createdPaymentDto.getPaymentId());
        assertThat(INITIATED).isEqualTo(createdPaymentDto.getStatus());
        verifyNoMoreInteractions(paymentRepository, paymentGateway, paymentActionStrategy);
    }

    @Test
    void createPayment_whenDataIntegrityViolationExceptionAndNotUniqueConstraintError_throwsTheException() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", idempotencyKey, null);
        Payment payment = PaymentDto.toPayment(dto, INITIATED);
        DataIntegrityViolationException dataIntegrityViolationException = new DataIntegrityViolationException("some cause");
        when(paymentRepository.save(payment)).thenThrow(dataIntegrityViolationException);

        assertThatThrownBy(() -> paymentService.createPayment(dto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("some cause");

        verifyNoMoreInteractions(paymentRepository, paymentGateway, paymentActionStrategy);
    }

    @Test
    void createPayment_whenUniqueConstraintErrorButDifferentIdempotencyKey_throwsTheException() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", idempotencyKey, null);
        Payment payment = PaymentDto.toPayment(dto, INITIATED);
        DataIntegrityViolationException dataIntegrityViolationException =
                new DataIntegrityViolationException("cause: " + UNIQUE_PAYMENT_IDEMPOTENCY_KEY);
        when(paymentRepository.save(payment)).thenThrow(dataIntegrityViolationException);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(dto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("cause: " + UNIQUE_PAYMENT_IDEMPOTENCY_KEY);

        verifyNoMoreInteractions(paymentRepository, paymentGateway, paymentActionStrategy);
    }

    @Test
    void createPayment_whenUniqueConstraintErrorAndSameIdempotencyKey_returnsExistingPaymentId() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", idempotencyKey, null);
        Payment payment = PaymentDto.toPayment(dto, INITIATED);
        DataIntegrityViolationException dataIntegrityViolationException =
                new DataIntegrityViolationException("cause: " + UNIQUE_PAYMENT_IDEMPOTENCY_KEY);
        when(paymentRepository.save(payment)).thenThrow(dataIntegrityViolationException);
        Payment existingPayment = mock(Payment.class);
        when(existingPayment.getPaymentId()).thenReturn(1L);
        when(existingPayment.getStatus()).thenReturn(INITIATED);
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        PaymentDto createdPaymentDto = paymentService.createPayment(dto);

        assertThat(1L).isEqualTo(createdPaymentDto.getPaymentId());
        assertThat(INITIATED).isEqualTo(createdPaymentDto.getStatus());
        verifyNoMoreInteractions(paymentRepository, paymentGateway, paymentActionStrategy);
    }

    @Test
    void executePayment_succeeds() {
        // mock payment entity
        long paymentId = 1L;
        UUID idempotencyKey = UUID.randomUUID();
        Payment mockedPayment = getMockedPayment(paymentId, idempotencyKey);
        when(mockedPayment.getStatus()).thenReturn(INITIATED).thenReturn(CAPTURED);
        // mock the payment strategies
        when(paymentActionStrategy.supports(INITIATED, PaymentStatusAction.START_PROCESSING)).thenReturn(true);
        when(paymentActionStrategy.supports(PROCESSING, PaymentStatusAction.CAPTURE)).thenReturn(true);
        when(paymentActionStrategy.getNextState()).thenReturn(PROCESSING).thenReturn(CAPTURED);
        // mock the repo responses
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(mockedPayment));
        when(paymentRepository.claimProcessingStatus(eq(paymentId), any(), any())).thenReturn(1);
        when(paymentRepository.setStatusIfCurrentStatusIs(paymentId, CAPTURED, PROCESSING)).thenReturn(1);

        PaymentDto responsePaymentDto = paymentService.executePayment(paymentId);

        assertThat(responsePaymentDto).isNotNull();
        assertThat(1L).isEqualTo(responsePaymentDto.getPaymentId());
        assertThat(PaymentStatus.CAPTURED).isEqualTo(responsePaymentDto.getStatus());
        verify(paymentGateway).executePayment(any(PaymentDto.class), eq(idempotencyKey));
        verifyNoMoreInteractions(paymentRepository, paymentGateway, paymentActionStrategy);
    }

    @Test
    void executePayment_whenPaymentNotFound_returnsNotFound() {
        long paymentId = 1L;
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.executePayment(paymentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("404 NOT_FOUND \"Payment with id [" + paymentId + "] does not exist\"");

        verifyNoMoreInteractions(paymentRepository, paymentGateway, paymentActionStrategy);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"CAPTURED", "FAILED"})
    void executePayment_whenPaymentAlreadyProcessed_returnsState(PaymentStatus paymentStatus) {
        long paymentId = 1L;
        Payment mockedPayment = mock(Payment.class);
        when(mockedPayment.getPaymentId()).thenReturn(paymentId);
        when(mockedPayment.getStatus()).thenReturn(paymentStatus);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(mockedPayment));
        when(paymentActionStrategy.supports(paymentStatus, PaymentStatusAction.START_PROCESSING)).thenReturn(false);

        PaymentDto responsePaymentDto = paymentService.executePayment(paymentId);

        assertThat(responsePaymentDto).isNotNull();
        assertThat(1L).isEqualTo(responsePaymentDto.getPaymentId());
        assertThat(paymentStatus).isEqualTo(responsePaymentDto.getStatus());
        verifyNoMoreInteractions(paymentRepository, paymentGateway, paymentActionStrategy);
    }

    @Test
    void executePayment_whenPaymentAlreadyInProcessingStatus_throwsException() {
        // mock the payment entity
        long paymentId = 1L;
        UUID idempotencyKey = UUID.randomUUID();
        Payment mockedPayment = getMockedPayment(paymentId, idempotencyKey);
        when(mockedPayment.getStatus()).thenReturn(INITIATED).thenReturn(CAPTURED);
        // mock the payment strategies
        when(paymentActionStrategy.supports(INITIATED, PaymentStatusAction.START_PROCESSING)).thenReturn(true);
        when(paymentActionStrategy.getNextState()).thenReturn(PROCESSING);
        // mock the repo responses
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(mockedPayment));
        when(paymentRepository.claimProcessingStatus(eq(paymentId), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> paymentService.executePayment(paymentId))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class)
                .hasMessage("Object of class [org.learnings.payments.paymentservice.domain.Payment] with identifier " +
                        "[1]: optimistic locking failed");

        verifyNoMoreInteractions(paymentRepository, paymentGateway, paymentActionStrategy);
    }

    @Test
    void executePayment_whenPaymentsGatewayFails_returnsStatusFailed() {
        // mock the payment entity
        long paymentId = 1L;
        UUID idempotencyKey = UUID.randomUUID();
        Payment mockedPayment = getMockedPayment(paymentId, idempotencyKey);
        when(mockedPayment.getStatus()).thenReturn(INITIATED).thenReturn(FAILED);
        // mock the payment strategies
        when(paymentActionStrategy.supports(INITIATED, PaymentStatusAction.START_PROCESSING)).thenReturn(true);
        when(paymentActionStrategy.supports(PROCESSING, PaymentStatusAction.FAIL)).thenReturn(true);
        when(paymentActionStrategy.getNextState()).thenReturn(PROCESSING).thenReturn(FAILED);
        // mock the repo responses
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(mockedPayment));
        when(paymentRepository.claimProcessingStatus(eq(paymentId), any(), any())).thenReturn(1);
        when(paymentRepository.setStatusIfCurrentStatusIs(paymentId, FAILED, PROCESSING)).thenReturn(1);
        doThrow(new RuntimeException("something went wrong"))
                .when(paymentGateway).executePayment(any(PaymentDto.class), eq(idempotencyKey));

        PaymentDto responsePaymentDto = paymentService.executePayment(paymentId);

        assertThat(responsePaymentDto).isNotNull();
        assertThat(1L).isEqualTo(responsePaymentDto.getPaymentId());
        assertThat(PaymentStatus.FAILED).isEqualTo(responsePaymentDto.getStatus());
        verifyNoMoreInteractions(paymentRepository, paymentGateway, paymentActionStrategy);
    }

    private Payment getMockedPayment(long paymentId, UUID idempotencyKey) {
        Payment mockedPayment = mock(Payment.class);
        when(mockedPayment.getPaymentId()).thenReturn(paymentId);
        when(mockedPayment.getIdempotencyKey()).thenReturn(idempotencyKey);

        return mockedPayment;
    }
}
