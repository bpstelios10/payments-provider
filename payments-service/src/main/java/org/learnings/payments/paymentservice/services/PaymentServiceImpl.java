package org.learnings.payments.paymentservice.services;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;
import org.learnings.payments.paymentservice.repositories.PaymentRepository;
import org.learnings.payments.paymentservice.services.dtos.PaymentDto;
import org.learnings.payments.paymentservice.services.statustransitions.PaymentActionStrategy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.learnings.payments.paymentservice.domain.Payment.UNIQUE_PAYMENT_IDEMPOTENCY_KEY;
import static org.learnings.payments.paymentservice.domain.PaymentStatus.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final List<PaymentActionStrategy> paymentActionStrategies;

    public PaymentServiceImpl(PaymentRepository paymentRepository, PaymentGateway paymentGateway, List<PaymentActionStrategy> paymentActionStrategies) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.paymentActionStrategies = paymentActionStrategies;
    }

    @Override
    public PaymentDto createPayment(PaymentDto paymentDto) {
        Payment payment = PaymentDto.toPayment(paymentDto, INITIATED);
        Payment savedPayment;

        try {
            savedPayment = paymentRepository.save(payment);
            log.debug("payment with id [{}] created at [{}]", savedPayment.getPaymentId(), savedPayment.getCreatedDate());
        } catch (DataIntegrityViolationException dae) {
            log.debug("payment creation failed with error: [{}]", dae.getMessage());

            return getPaymentIdWhenIsRetry(paymentDto, dae);
        }

        return PaymentDto.fromPayment(savedPayment);
    }

    // This method is not annotated as transactional cause the rest call will keep it open for a long time
    // so we make it atomic by using Status and no need to rollback in any case of failure.
    // this is achieved with the PROCESSING status with a timestamp and using idempotency-key on the downstream call.
    @Override
    public PaymentDto executePayment(long paymentId) {
        PaymentDto paymentDto = getPaymentDtoById(paymentId);

        // return fast, if it is not in valid state for processing
        Optional<PaymentStatus> processingStatus = getNextStatus(paymentDto.getStatus(), PaymentStatusAction.START_PROCESSING);
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

        // TODO if this update fails, then something weird is happening. might need to throw some error.
        //  will also need to check this so later only 1 thread is allowed to do things like notifications, etc
        PaymentStatusAction finalNextAction = nextAction;
        PaymentStatus nextStatus = getNextStatus(currentStatus, nextAction)
                .orElseThrow(() -> new IllegalStateException("No handler for [" + currentStatus + " + " + finalNextAction + "]"));

        paymentRepository.setStatusIfCurrentStatusIs(paymentId, nextStatus, currentStatus);

        Payment updated = paymentRepository.findById(paymentId).orElseThrow();

        return PaymentDto.fromPayment(updated);
    }

    /*
        This is needed because the initial transaction will be marked as dirty. but the previous transaction turns
        the whole class as a proxy. so the next lines need to go to some utility class. then this Transactional will work
        @Transactional(propagation = Propagation.REQUIRES_NEW)
    */
    private PaymentDto getPaymentIdWhenIsRetry(PaymentDto paymentDto, DataIntegrityViolationException dae) {
        if (dae.getMessage().contains(UNIQUE_PAYMENT_IDEMPOTENCY_KEY)) {
            Optional<Payment> byIdempotencyKey = paymentRepository.findByIdempotencyKey(paymentDto.getIdempotencyKey());

            if (byIdempotencyKey.isPresent()) {
                return PaymentDto.fromPayment(byIdempotencyKey.get());
            }
        }

        throw dae;
    }

    private @NonNull PaymentDto getPaymentDtoById(long paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new ResponseStatusException(NOT_FOUND, "Payment with id [" + paymentId + "] does not exist"));
        return PaymentDto.fromPayment(payment);
    }

    private void lockPaymentBySettingStatusProcessing(long paymentId) {
        Instant now = Instant.now();
        Instant timeout = now.minusSeconds(10);
        int isStatusUpdated = paymentRepository.claimProcessingStatus(paymentId, now, timeout);
        if (isStatusUpdated == 0) {
            // TODO i have to check again if the status is now CAPTURED or FAILED to be accurate to avoid retries
            throw new ObjectOptimisticLockingFailureException(Payment.class, paymentId);
        }
    }

    private Optional<PaymentStatus> getNextStatus(PaymentStatus currentStatus, PaymentStatusAction nextAction) {
        Optional<PaymentActionStrategy> paymentActionStrategy = paymentActionStrategies.stream()
                .filter(s -> s.supports(currentStatus, nextAction))
                .findFirst();

        return paymentActionStrategy.map(PaymentActionStrategy::getNextState);
    }
}
