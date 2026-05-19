package org.learnings.payments.paymentservice.application;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.learnings.payments.paymentservice.application.dtos.PaymentDto;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;
import org.learnings.payments.paymentservice.domain.repositories.PaymentRepository;
import org.learnings.payments.paymentservice.domain.statustransitions.PaymentActionResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.learnings.payments.paymentservice.domain.PaymentStatus.PROCESSING;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
public class ExecutePaymentUseCase {

    @Value("${service.processing-status-timeout-seconds:10}")
    private int PROCESSING_STATUS_TIMEOUT_DURATION_SECS;
    private final PaymentRepository paymentRepository;
    private final PaymentActionResolver paymentActionResolver;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;
    private final EventMessagePublisher eventMessagePublisher;

    public ExecutePaymentUseCase(PaymentRepository paymentRepository, PaymentActionResolver paymentActionResolver,
                                 PaymentGateway paymentGateway, TransactionTemplate transactionTemplate, EventMessagePublisher eventMessagePublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentActionResolver = paymentActionResolver;
        this.paymentGateway = paymentGateway;
        this.transactionTemplate = transactionTemplate;
        this.eventMessagePublisher = eventMessagePublisher;
    }

    /*
     * This method is not annotated as transactional cause the rest call will keep it open for a long time
     * so we make it atomic by using Status and no need to rollback in any case of failure.
     * this is achieved with the PROCESSING status with a timestamp and using idempotency-key on the downstream call.
     */
    public PaymentDto execute(long paymentId) {
        PaymentDto paymentDto = getPaymentDtoById(paymentId);

        // return fast, if it is not in valid state for processing
        Optional<PaymentStatus> processingStatus =
                paymentActionResolver.getNextStatus(paymentDto.getStatus(), PaymentStatusAction.START_PROCESSING);
        if (processingStatus.isEmpty()) {
            return paymentDto;
        }

        lockPaymentBySettingStatusProcessing(paymentId);

        PaymentStatus currentStatus = PROCESSING;
        PaymentStatusAction nextAction = PaymentStatusAction.CAPTURE;

        try {
            paymentGateway.executePayment(paymentDto, paymentDto.getIdempotencyKey());
        } catch (Exception ex) {
            // TODO not all payment failures should be failed. eg timeouts, etc should remain processing
            nextAction = PaymentStatusAction.FAIL;
        }

        String nextActionName = nextAction.name();
        PaymentStatus nextStatus = paymentActionResolver.getNextStatus(currentStatus, nextAction)
                .orElseThrow(() -> new IllegalStateException("No handler for [" + currentStatus + " + " + nextActionName + "]"));

        Payment updated = updateStatusAndPublishEvent(paymentId, nextStatus, currentStatus);

        return PaymentDto.fromPayment(updated);
    }

    private @NonNull PaymentDto getPaymentDtoById(long paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new ResponseStatusException(NOT_FOUND, "Payment with id [" + paymentId + "] does not exist"));
        return PaymentDto.fromPayment(payment);
    }

    private void lockPaymentBySettingStatusProcessing(long paymentId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant timeout = now.minusSeconds(PROCESSING_STATUS_TIMEOUT_DURATION_SECS).truncatedTo(ChronoUnit.MILLIS);
        int isStatusUpdated = paymentRepository.claimProcessingStatus(paymentId, now, timeout);
        if (isStatusUpdated == 0) {
            // TODO i have to check again if the status is now CAPTURED or FAILED to be accurate to avoid retries
            throw new ObjectOptimisticLockingFailureException(Payment.class, paymentId);
        }
    }

    /*
     * we first set the current status, if it is valid transition. this is to make it thread safe
     * if this fails, so some other thread already updated the status, thas's fine cause we get the updated payment next
     * in any case, we are safe to publish the event, cause we use paymentId+status as idempotency key, for retries.
     */
    private Payment updateStatusAndPublishEvent(long paymentId, PaymentStatus nextStatus, PaymentStatus currentStatus) {
        return transactionTemplate.execute(_ -> {
            paymentRepository.setStatusIfCurrentStatusIs(paymentId, nextStatus, currentStatus);
            Payment updated = paymentRepository.findById(paymentId).orElseThrow();

            eventMessagePublisher.publish(
                    new EventMessage(paymentId, "PAYMENT", updated.getStatus().name(), updated));
            return updated;
        });
    }
}
