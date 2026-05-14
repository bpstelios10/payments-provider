package org.learnings.payments.paymentservice.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnings.payments.paymentservice.application.dtos.PaymentDto;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.repositories.PaymentRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.learnings.payments.paymentservice.application.PaymentUseCaseTestUtils.getMockedPayment;
import static org.learnings.payments.paymentservice.domain.Payment.UNIQUE_PAYMENT_IDEMPOTENCY_KEY;
import static org.learnings.payments.paymentservice.domain.PaymentStatus.INITIATED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePaymentUseCaseTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private EventMessagePublisher eventMessagePublisher;
    @Mock
    private TransactionTemplate transactionTemplate;
    @InjectMocks
    private CreatePaymentUseCase createPaymentUseCase;

    @Test
    void execute_succeeds() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", idempotencyKey, null);
        Payment payment = PaymentDto.toPayment(dto, INITIATED);
        Payment savedPayment = getMockedPayment(1L, idempotencyKey, INITIATED);
        when(savedPayment.getCreatedDate()).thenReturn(Instant.now());
        when(paymentRepository.save(payment)).thenReturn(savedPayment);
        mockTransactionTemplateToExecuteCallback();
        EventMessage event = new EventMessage(1L, "PAYMENT", INITIATED.name(), savedPayment);
        doNothing().when(eventMessagePublisher).publish(event);

        PaymentDto createdPaymentDto = createPaymentUseCase.execute(dto);

        assertThat(1L).isEqualTo(createdPaymentDto.getPaymentId());
        assertThat(INITIATED).isEqualTo(createdPaymentDto.getStatus());
        verifyNoMoreMockInteractions();
    }

    @Test
    void execute_whenUniqueConstraintErrorButDifferentIdempotencyKey_throwsTheException() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", idempotencyKey, null);
        Payment payment = PaymentDto.toPayment(dto, INITIATED);
        DataIntegrityViolationException dataIntegrityViolationException =
                new DataIntegrityViolationException("cause: " + UNIQUE_PAYMENT_IDEMPOTENCY_KEY);
        when(paymentRepository.save(payment)).thenThrow(dataIntegrityViolationException);
        mockTransactionTemplateToExecuteCallback();
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> createPaymentUseCase.execute(dto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("cause: " + UNIQUE_PAYMENT_IDEMPOTENCY_KEY);

        verifyNoMoreMockInteractions();
    }

    @Test
    void execute_whenUniqueConstraintErrorAndSameIdempotencyKey_returnsExistingPaymentId() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", idempotencyKey, null);
        Payment payment = PaymentDto.toPayment(dto, INITIATED);
        DataIntegrityViolationException dataIntegrityViolationException =
                new DataIntegrityViolationException("cause: " + UNIQUE_PAYMENT_IDEMPOTENCY_KEY);
        when(paymentRepository.save(payment)).thenThrow(dataIntegrityViolationException);
        Payment existingPayment = getMockedPayment(1L, idempotencyKey, INITIATED);
        mockTransactionTemplateToExecuteCallback();
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        PaymentDto createdPaymentDto = createPaymentUseCase.execute(dto);

        assertThat(1L).isEqualTo(createdPaymentDto.getPaymentId());
        assertThat(INITIATED).isEqualTo(createdPaymentDto.getStatus());
        verifyNoMoreMockInteractions();
    }

    @Test
    void execute_whenOutboxSaveFails_throws() {
        PaymentDto dto = new PaymentDto(new BigDecimal(100), "EU", "merch-1", UUID.randomUUID(), null);
        Payment payment = PaymentDto.toPayment(dto, INITIATED);
        Payment savedPayment = getMockedPayment(1L);
        when(paymentRepository.save(payment)).thenReturn(savedPayment);
        mockTransactionTemplateToExecuteCallback();
        EventMessage event = new EventMessage(1L, "PAYMENT", INITIATED.name(), savedPayment);
        doThrow(new CannotGetJdbcConnectionException("oops")).when(eventMessagePublisher).publish(event);

        assertThatThrownBy(() -> createPaymentUseCase.execute(dto))
                .isInstanceOf(DataAccessException.class);

        verifyNoMoreMockInteractions(savedPayment);
    }

    private void verifyNoMoreMockInteractions(Object... extraMocks) {
        Object[] mocks = Stream.concat(
                Stream.of(paymentRepository, eventMessagePublisher, transactionTemplate),
                extraMocks == null ? Stream.empty() : Stream.of(extraMocks)
        ).toArray();

        verifyNoMoreInteractions(mocks);
    }

    @SuppressWarnings("ConstantConditions")
    void mockTransactionTemplateToExecuteCallback() {
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);

            return callback.doInTransaction(null); // simulate  behavior
        }).when(transactionTemplate).execute(any());
    }
}
