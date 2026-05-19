package org.learnings.payments.paymentservice.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.learnings.payments.paymentservice.application.dtos.PaymentDto;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;
import org.learnings.payments.paymentservice.domain.repositories.PaymentRepository;
import org.learnings.payments.paymentservice.domain.statustransitions.PaymentActionResolver;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.learnings.payments.paymentservice.application.PaymentUseCaseTestUtils.getMockedPayment;
import static org.learnings.payments.paymentservice.domain.PaymentStatus.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutePaymentUseCaseTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentActionResolver paymentActionResolver;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private EventMessagePublisher eventMessagePublisher;
    @InjectMocks
    private ExecutePaymentUseCase executePaymentUseCase;

    @Test
    void execute_succeeds() {
        // mock payment entity
        long paymentId = 1L;
        UUID idempotencyKey = UUID.randomUUID();
        Payment mockedPayment = getMockedPayment(1L, idempotencyKey, INITIATED, CAPTURED);
        // mock the payment strategies
        when(paymentActionResolver.getNextStatus(INITIATED, PaymentStatusAction.START_PROCESSING)).thenReturn(Optional.of(PROCESSING));
        when(paymentActionResolver.getNextStatus(PROCESSING, PaymentStatusAction.CAPTURE)).thenReturn(Optional.of(CAPTURED));
        // mock the repo responses
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(mockedPayment));
        when(paymentRepository.claimProcessingStatus(eq(paymentId), any(), any())).thenReturn(1);
        when(paymentRepository.setStatusIfCurrentStatusIs(paymentId, CAPTURED, PROCESSING)).thenReturn(1);
        mockTransactionTemplateToExecuteCallback();

        PaymentDto responsePaymentDto = executePaymentUseCase.execute(paymentId);

        assertThat(responsePaymentDto).isNotNull();
        assertThat(1L).isEqualTo(responsePaymentDto.getPaymentId());
        assertThat(PaymentStatus.CAPTURED).isEqualTo(responsePaymentDto.getStatus());
        verify(paymentGateway).executePayment(any(PaymentDto.class), eq(idempotencyKey));
        verify(eventMessagePublisher).publish(any());
        verifyNoMoreMockInteractions();
    }

    @Test
    void execute_whenNotFound_returnsNotFound() {
        long paymentId = 1L;
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executePaymentUseCase.execute(paymentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessage("404 NOT_FOUND \"Payment with id [" + paymentId + "] does not exist\"");

        verifyNoMoreMockInteractions();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"CAPTURED", "FAILED"})
    void execute_whenAlreadyProcessed_returnsState(PaymentStatus paymentStatus) {
        long paymentId = 1L;
        Payment mockedPayment = getMockedPayment(paymentId);
        when(mockedPayment.getStatus()).thenReturn(paymentStatus);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(mockedPayment));
        when(paymentActionResolver.getNextStatus(paymentStatus, PaymentStatusAction.START_PROCESSING)).thenReturn(Optional.empty());

        PaymentDto responsePaymentDto = executePaymentUseCase.execute(paymentId);

        assertThat(responsePaymentDto).isNotNull();
        assertThat(1L).isEqualTo(responsePaymentDto.getPaymentId());
        assertThat(paymentStatus).isEqualTo(responsePaymentDto.getStatus());
        verifyNoMoreMockInteractions();
    }

    @Test
    void execute_whenAlreadyInProcessingStatus_throwsException() {
        // mock the payment entity
        long paymentId = 1L;
        UUID idempotencyKey = UUID.randomUUID();
        Payment mockedPayment = getMockedPayment(paymentId, idempotencyKey, INITIATED, CAPTURED);
        // mock the payment strategies
        when(paymentActionResolver.getNextStatus(INITIATED, PaymentStatusAction.START_PROCESSING)).thenReturn(Optional.of(PROCESSING));
        // mock the repo responses
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(mockedPayment));
        when(paymentRepository.claimProcessingStatus(eq(paymentId), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> executePaymentUseCase.execute(paymentId))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class)
                .hasMessage("Object of class [org.learnings.payments.paymentservice.domain.Payment] with identifier " +
                        "[1]: optimistic locking failed");

        verifyNoMoreMockInteractions();
    }

    @Test
    void execute_whenPaymentsGatewayFails_returnsStatusFailed() {
        // mock the payment entity
        long paymentId = 1L;
        UUID idempotencyKey = UUID.randomUUID();
        Payment mockedPayment = getMockedPayment(paymentId, idempotencyKey, INITIATED, FAILED);
        // mock the payment strategies
        when(paymentActionResolver.getNextStatus(INITIATED, PaymentStatusAction.START_PROCESSING)).thenReturn(Optional.of(PROCESSING));
        when(paymentActionResolver.getNextStatus(PROCESSING, PaymentStatusAction.FAIL)).thenReturn(Optional.of(FAILED));
        // mock the repo responses
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(mockedPayment));
        when(paymentRepository.claimProcessingStatus(eq(paymentId), any(), any())).thenReturn(1);
        when(paymentRepository.setStatusIfCurrentStatusIs(paymentId, FAILED, PROCESSING)).thenReturn(1);
        doThrow(new RuntimeException("something went wrong"))
                .when(paymentGateway).executePayment(any(PaymentDto.class), eq(idempotencyKey));
        mockTransactionTemplateToExecuteCallback();

        PaymentDto responsePaymentDto = executePaymentUseCase.execute(paymentId);

        assertThat(responsePaymentDto).isNotNull();
        assertThat(1L).isEqualTo(responsePaymentDto.getPaymentId());
        assertThat(PaymentStatus.FAILED).isEqualTo(responsePaymentDto.getStatus());
        verify(eventMessagePublisher).publish(any());
        verifyNoMoreMockInteractions();
    }

    @SuppressWarnings("ConstantConditions")
    void mockTransactionTemplateToExecuteCallback() {
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);

            return callback.doInTransaction(null); // simulate  behavior
        }).when(transactionTemplate).execute(any());
    }

    private void verifyNoMoreMockInteractions(Object... extraMocks) {
        Object[] mocks = Stream.concat(
                Stream.of(paymentRepository, paymentActionResolver, paymentGateway, transactionTemplate, eventMessagePublisher),
                extraMocks == null ? Stream.empty() : Stream.of(extraMocks)
        ).toArray();

        verifyNoMoreInteractions(mocks);
    }
}
