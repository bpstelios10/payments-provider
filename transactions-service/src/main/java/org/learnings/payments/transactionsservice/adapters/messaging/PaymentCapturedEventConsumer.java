package org.learnings.payments.transactionsservice.adapters.messaging;

import lombok.extern.slf4j.Slf4j;
import org.learnings.payments.transactionsservice.adapters.messaging.model.EventEnvelope;
import org.learnings.payments.transactionsservice.adapters.messaging.model.EventType;
import org.learnings.payments.transactionsservice.adapters.messaging.model.PaymentEventPayload;
import org.learnings.payments.transactionsservice.services.LedgerService;
import org.learnings.payments.transactionsservice.services.dtos.LedgerEntryDto;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class PaymentCapturedEventConsumer {

    private final LedgerService ledgerService;

    public PaymentCapturedEventConsumer(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @KafkaListener(topics = "PAYMENT_CAPTURED")
    public void consume(EventEnvelope<PaymentEventPayload> envelope) {
        log.debug("*** Received payment captured event: [{}]", envelope);
        if (envelope == null || envelope.eventType() != EventType.PAYMENT_CAPTURED) {
            throw new IllegalArgumentException("not a payment captured event");
        }

        PaymentCapturedEvent paymentCapturedEvent = PaymentCapturedEvent.fromEventEnvelope(envelope);
        LedgerEntryDto ledgerEntryDto = PaymentCapturedEvent.toLedgerEntryDto(paymentCapturedEvent);

        ledgerService.process(ledgerEntryDto);
    }
}
