package org.learnings.payments.paymentservice.componenttests;

import org.learnings.payments.messaging.outbox.jpa.OutboxEvent;
import org.learnings.payments.messaging.outbox.jpa.OutboxEventRepository;
import org.learnings.payments.paymentservice.domain.Payment;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class PaymentsComponentTestBase {

    @Autowired
    protected OutboxEventRepository outboxEventRepository;
    @Autowired protected TestPaymentRepository repository;
    @Autowired protected JsonMapper jsonMapper;

    void assertThatOutboxEventIsCreated(Payment payment) {
        Long paymentId = payment.getPaymentId();
        String paymentStatus = payment.getStatus().name();

        Optional<OutboxEvent> outboxEventOptional = outboxEventRepository
                .findByAggregateIdAndAggregateTypeAndEventType(paymentId, "PAYMENT", paymentStatus);
        assertThat(outboxEventOptional).isNotEmpty();
        OutboxEvent outboxEvent = outboxEventOptional.get();

        assertThat(outboxEvent.getAggregateId()).isEqualTo(paymentId);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("PAYMENT");
        assertThat(outboxEvent.getEventType()).isEqualTo(paymentStatus);
        String expectedPayload = jsonMapper.writeValueAsString(payment);
        assertThat(expectedPayload).isEqualTo(outboxEvent.getPayload());
    }

    void assertThatPaymentsAndOutboxTablesHaveNewRecords(int numberOfRecords) {
        List<Payment> payments = repository.findAll();
        assertThat(payments).hasSize(numberOfRecords);
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(numberOfRecords);
    }
}
