package org.learnings.payments.paymentservice.services;

import lombok.extern.slf4j.Slf4j;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.repositories.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public Long createPayment(PaymentDto paymentDto) {
        Payment payment = PaymentDto.toPayment(paymentDto, "pending");

        Payment savedPayment = paymentRepository.save(payment);

        log.debug("payment with id [{}] created at [{}]", savedPayment.getPaymentId(), savedPayment.getCreatedDate());

        return savedPayment.getPaymentId();
    }
}
