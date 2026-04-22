package org.learnings.payments.paymentservice.services;

import lombok.extern.slf4j.Slf4j;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.repositories.PaymentRepository;
import org.learnings.payments.paymentservice.services.dtos.PaymentDto;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.learnings.payments.paymentservice.domain.PaymentStatus.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentServiceImpl(PaymentRepository paymentRepository, PaymentGateway paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    @Override
    public PaymentDto createPayment(PaymentDto paymentDto) {
        Payment payment = PaymentDto.toPayment(paymentDto, INITIATED);
        Payment savedPayment;

        try {
            savedPayment = paymentRepository.save(payment);
            log.debug("payment with id [{}] created at [{}]", savedPayment.getPaymentId(), savedPayment.getCreatedDate());
        } catch (DataAccessException dae) {
            log.debug("payment creation failed with error: [{}]", dae.getMessage());

            return getPaymentIdWhenIsRetry(paymentDto, dae);
        }

        return PaymentDto.fromPayment(savedPayment);
    }

    // This method is not annotated as transactional cause the rest call will keep it open for a long time
    // so we need to manually check the version for conflicts
    @Override
    public PaymentDto executePayment(long paymentId) {
        Payment payment = paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new ResponseStatusException(NOT_FOUND, "Payment with id [" + paymentId + "] does not exist"));
        PaymentDto paymentDto = PaymentDto.fromPayment(payment);

        // return fast, if it is already captured or rejected
        if (List.of(CAPTURED, FAILED).contains(paymentDto.getStatus())) {
            return paymentDto;
        }

        // from now on, we want to be thread safe,
        // but also re-process potential payments that were marked as processed but then server failed
        paymentRepository.setStatusIfCurrentStatusIs(paymentId, PROCESSING, INITIATED);
        PaymentStatus newStatus = CAPTURED;

        try {
            paymentGateway.executePayment(paymentDto, paymentDto.getIdempotencyKey());
        } catch (Exception ex) {
            // TODO not all payment failures should be failed. eg timeouts, etc should remain processing
            newStatus = FAILED;
        }

        int isStatusUpdated = paymentRepository.setStatusIfCurrentStatusIs(paymentId, newStatus, PROCESSING);
        // i dont need to fail here, but it is a nice tech exercise
        // for idempotent endpoint, i should just return the latest status from the DB
        if (isStatusUpdated == 0) {
            throw new ObjectOptimisticLockingFailureException(Payment.class, paymentId);
        }

        Payment updated = paymentRepository.findById(paymentId).orElseThrow();

        return PaymentDto.fromPayment(updated);
    }

//    This is needed because the initial transaction will be marked as dirty. but the previous transaction turns
//    the whole class as a proxy. so the next lines need to go to some utility class. then this Transactional will work
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private PaymentDto getPaymentIdWhenIsRetry(PaymentDto paymentDto, DataAccessException dae) {
        if (dae instanceof DataIntegrityViolationException && dae.getMessage().contains("UNIQUE_IDEMTOTENCY_KEY")) {
            Optional<Payment> byIdempotencyKey = paymentRepository.findByIdempotencyKey(paymentDto.getIdempotencyKey());

            if (byIdempotencyKey.isPresent()) {
                return PaymentDto.fromPayment(byIdempotencyKey.get());
            }
        }

        throw dae;
    }
}
