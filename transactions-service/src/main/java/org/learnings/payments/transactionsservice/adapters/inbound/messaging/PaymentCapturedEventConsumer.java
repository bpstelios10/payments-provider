package org.learnings.payments.transactionsservice.adapters.inbound.messaging;

import lombok.extern.slf4j.Slf4j;
import org.learnings.payments.messaging.events.EventEnvelope;
import org.learnings.payments.messaging.events.EventType;
import org.learnings.payments.transactionsservice.adapters.inbound.messaging.model.PaymentEventPayload;
import org.learnings.payments.transactionsservice.application.ProcessLedgerEntryUseCase;
import org.learnings.payments.transactionsservice.application.dtos.LedgerEntryDto;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class PaymentCapturedEventConsumer {

    private final ProcessLedgerEntryUseCase processLedgerEntryUseCase;

    public PaymentCapturedEventConsumer(ProcessLedgerEntryUseCase processLedgerEntryUseCase) {
        this.processLedgerEntryUseCase = processLedgerEntryUseCase;
    }

    @KafkaListener(topics = "PAYMENT_CAPTURED")
    public void consume(EventEnvelope<PaymentEventPayload> envelope) {
        log.debug("*** Received payment captured event: [{}]", envelope);
        if (envelope == null || envelope.eventType() != EventType.PAYMENT_CAPTURED) {
            throw new IllegalArgumentException("not a payment captured event");
        }

        PaymentCapturedEvent paymentCapturedEvent = PaymentCapturedEvent.fromEventEnvelope(envelope);
        LedgerEntryDto ledgerEntryDto = PaymentCapturedEvent.toLedgerEntryDto(paymentCapturedEvent);

        processLedgerEntryUseCase.execute(ledgerEntryDto);
    }
}
