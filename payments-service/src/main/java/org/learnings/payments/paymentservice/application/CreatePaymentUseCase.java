package org.learnings.payments.paymentservice.application;

import lombok.extern.slf4j.Slf4j;
import org.learnings.payments.paymentservice.application.dtos.PaymentDto;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.repositories.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.learnings.payments.paymentservice.domain.PaymentStatus.INITIATED;

@Slf4j
@Service
public class CreatePaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final EventMessagePublisher eventMessagePublisher;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    public CreatePaymentUseCase(PaymentRepository paymentRepository, EventMessagePublisher eventMessagePublisher,
                                JsonMapper jsonMapper, TransactionTemplate transactionTemplate) {
        this.paymentRepository = paymentRepository;
        this.eventMessagePublisher = eventMessagePublisher;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /*
     * we cant mark this method as Transactional cause in case of failure, the transaction is marked for rollback. but
     * then in the catch block, we need to do another payment request, but the transaction is now dirty. issues!
     * we resolve by using saveAndAudit for atomicity. we use TransactionTemplate in there, to keep the transaction
     * scope narrower, inside the saveAndAudit method only. (cant mark saveAndAudit as Transactional cause it is private
     * and internal method). Now, the catch block is safe. We use the repo, so another independent transaction, in there
     */
    public PaymentDto execute(PaymentDto paymentDto) {
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

    @SuppressWarnings("SameParameterValue")
    private Payment saveAndAudit(Payment payment, PaymentStatus paymentStatus) {
        return transactionTemplate.execute(_ -> {
            Payment saved = paymentRepository.save(payment);

            EventMessage event = new EventMessage(saved.getPaymentId(), "PAYMENT", paymentStatus.name(),
                    jsonMapper.writeValueAsString(saved));
            eventMessagePublisher.publish(event);

            return saved;
        });
    }
}
