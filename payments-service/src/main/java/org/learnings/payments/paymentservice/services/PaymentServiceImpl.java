package org.learnings.payments.paymentservice.services;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;
import org.learnings.payments.paymentservice.infrastructure.outbox.OutboxEvent;
import org.learnings.payments.paymentservice.infrastructure.outbox.OutboxRepository;
import org.learnings.payments.paymentservice.repositories.PaymentRepository;
import org.learnings.payments.paymentservice.services.dtos.PaymentDto;
import org.learnings.payments.paymentservice.services.statustransitions.PaymentActionStrategy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.learnings.payments.paymentservice.domain.PaymentStatus.INITIATED;
import static org.learnings.payments.paymentservice.domain.PaymentStatus.PROCESSING;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final List<PaymentActionStrategy> paymentActionStrategies;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;

    public PaymentServiceImpl(PaymentRepository paymentRepository, OutboxRepository outboxRepository, JsonMapper jsonMapper,
                              List<PaymentActionStrategy> paymentActionStrategies, PaymentGateway paymentGateway, TransactionTemplate transactionTemplate) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.paymentActionStrategies = paymentActionStrategies;
        this.paymentGateway = paymentGateway;
        this.transactionTemplate = transactionTemplate;
    }

    /*
     * we cant mark this method as Transactional cause in case of failure, the transaction is marked for rollback. but
     * then in the catch block, we need to do another payment request, but the transaction is now dirty. issues!
     * we resolve by using saveAndAudit for atomicity. we use TransactionTemplate in there, to keep the transaction
     * scope narrower, inside the saveAndAudit method only. (cant mark saveAndAudit as Transactional cause it is private
     * and internal method). Now, the catch block is safe. We use the repo, so another independent transaction, in there
     */
    @Override
    public PaymentDto createPayment(PaymentDto paymentDto) {
        Payment payment = PaymentDto.toPayment(paymentDto, INITIATED);
        Payment savedPayment;

        try {
            savedPayment = saveAndAudit(payment, INITIATED);
            log.debug("payment with id [{}] created at [{}]", savedPayment.getPaymentId(), savedPayment.getCreatedDate());
        } catch (DataIntegrityViolationException dae) {
            log.debug("payment creation failed with error: [{}]", dae.getMessage());
            Optional<Payment> byIdempotencyKey = paymentRepository.findByIdempotencyKey(paymentDto.getIdempotencyKey());

            return PaymentDto.fromPayment(byIdempotencyKey.orElseThrow(() -> dae));
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

    @SuppressWarnings("SameParameterValue")
    private Payment saveAndAudit(Payment payment, PaymentStatus paymentStatus) {
        return transactionTemplate.execute(_ -> {
            Payment saved = paymentRepository.save(payment);

            OutboxEvent event = new OutboxEvent(saved.getPaymentId(), "PAYMENT",
                    paymentStatus.name(), jsonMapper.writeValueAsString(saved));
            outboxRepository.save(event);

            return saved;
        });
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
