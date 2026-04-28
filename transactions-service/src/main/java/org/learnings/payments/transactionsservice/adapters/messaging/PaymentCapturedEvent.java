package org.learnings.payments.transactionsservice.adapters.messaging;

import org.learnings.payments.transactionsservice.adapters.messaging.model.EventEnvelope;
import org.learnings.payments.transactionsservice.adapters.messaging.model.PaymentEventPayload;
import org.learnings.payments.transactionsservice.domain.LedgerType;
import org.learnings.payments.transactionsservice.services.dtos.LedgerEntryDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record PaymentCapturedEvent(
        UUID eventId, Long paymentId, BigDecimal amount, String currency, Instant paymentOccurredAt) {

    static PaymentCapturedEvent fromEventEnvelope(EventEnvelope<? extends PaymentEventPayload> envelope) {
        return new PaymentCapturedEvent(
                envelope.eventId(),
                envelope.payload().paymentId(),
                envelope.payload().amount(),
                envelope.payload().currency(),
                envelope.payload().occurredAt().truncatedTo(ChronoUnit.MILLIS));
    }

    static LedgerEntryDto toLedgerEntryDto(PaymentCapturedEvent event) {
        return new LedgerEntryDto(
                event.eventId,
                event.paymentId(),
                LedgerType.CAPTURED,
                event.amount(),
                event.currency(),
                null,
                event.paymentOccurredAt().truncatedTo(ChronoUnit.MILLIS)
        );
    }
}
