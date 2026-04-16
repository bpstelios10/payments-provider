package org.learnings.payments.paymentservice.services;

import lombok.extern.slf4j.Slf4j;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.repositories.PaymentRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

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
//    @Transactional
    public PaymentResponseDto createPayment(PaymentDto paymentDto) {
        Payment payment = PaymentDto.toPayment(paymentDto, "pending");

        try {
            Payment savedPayment = paymentRepository.save(payment);

            log.debug("payment with id [{}] created at [{}]", savedPayment.getPaymentId(), savedPayment.getCreatedDate());

            return PaymentResponseDto.fromPayment(savedPayment);
        } catch (DataAccessException dae) {
            return getTransactionIdWhenIsRetry(paymentDto, dae);
        }
    }

    // This method is not annotated as transactional cause the rest call will keep it open for a long time
    // so we need to manually check the version for conflicts
    @Override
    public PaymentResponseDto executePayment(long paymentId) {
        Optional<Payment> payment = paymentRepository.findById(paymentId);
        long version = payment
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment with id [" + paymentId + "] does not exist"))
                .getVersion();

        paymentGateway.executePayment(PaymentDto.fromPayment(payment.get()));

        int updated = paymentRepository.updateIfVersionMatches(paymentId, version, "executed");

        if (updated == 0) {
            throw new ObjectOptimisticLockingFailureException(Payment.class, paymentId);
        }

        return new PaymentResponseDto(paymentId, "executed");
    }

//    This is needed because the initial transaction will be marked as dirty. but the previous transaction turns
//    the whole class as a proxy. so the next lines need to go to some utility class. then this Transactional will work
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private PaymentResponseDto getTransactionIdWhenIsRetry(PaymentDto paymentDto, DataAccessException dae) {
        if (dae instanceof DataIntegrityViolationException && dae.getMessage().contains("UNIQUE_IDEMTOTENCY_KEY")) {
            Optional<Payment> byIdempotencyKey = paymentRepository.findByIdempotencyKey(paymentDto.idempotencyKey());

            if (byIdempotencyKey.isPresent()) {
                return new PaymentResponseDto(byIdempotencyKey.get().getPaymentId(), byIdempotencyKey.get().getStatus());
            }
        }

        throw dae;
    }
}
