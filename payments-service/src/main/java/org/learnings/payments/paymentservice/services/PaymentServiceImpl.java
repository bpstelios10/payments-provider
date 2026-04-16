package org.learnings.payments.paymentservice.services;

import lombok.extern.slf4j.Slf4j;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.repositories.PaymentRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
//    @Transactional
    public Long createPayment(PaymentDto paymentDto) {
        Payment payment = PaymentDto.toPayment(paymentDto, "pending");

        try {
            Payment savedPayment = paymentRepository.save(payment);

            log.debug("payment with id [{}] created at [{}]", savedPayment.getPaymentId(), savedPayment.getCreatedDate());

            return savedPayment.getPaymentId();
        } catch (DataAccessException dae) {
            return getTransactionIdWhenIsRetry(paymentDto, dae);
        }
    }

//    This is needed because the initial transaction will be marked as dirty. but the previous transaction turns
//    the whole class as a proxy. so the next lines need to go to some utility class. then this Transactional will work
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private Long getTransactionIdWhenIsRetry(PaymentDto paymentDto, DataAccessException dae) {
        if (dae instanceof DataIntegrityViolationException && dae.getMessage().contains("UNIQUE_IDEMTOTENCY_KEY")) {
            Optional<Payment> byIdempotencyKey = paymentRepository.findByIdempotencyKey(paymentDto.idempotencyKey());

            if (byIdempotencyKey.isPresent()) {
                return byIdempotencyKey.get().getPaymentId();
            }
        }

        throw dae;
    }
}
