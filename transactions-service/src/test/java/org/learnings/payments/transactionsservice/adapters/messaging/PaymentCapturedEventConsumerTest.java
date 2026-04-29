package org.learnings.payments.transactionsservice.adapters.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnings.payments.transactionsservice.adapters.messaging.model.EventEnvelope;
import org.learnings.payments.transactionsservice.adapters.messaging.model.PaymentEventPayload;
import org.learnings.payments.transactionsservice.services.LedgerService;
import org.learnings.payments.transactionsservice.services.dtos.LedgerEntryDto;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.learnings.payments.transactionsservice.adapters.messaging.model.EventType.PAYMENT_CAPTURED;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentCapturedEventConsumerTest {

    @Mock
    private LedgerService ledgerService;
    @InjectMocks
    private PaymentCapturedEventConsumer consumer;

    @Test
    void consume_succeeds() {
        Instant paymentOccurredAt = Instant.now();
        PaymentEventPayload payload =
                new PaymentEventPayload(123L, BigDecimal.valueOf(100.00), "USD", paymentOccurredAt);
        EventEnvelope<PaymentEventPayload> envelope =
                new EventEnvelope<>(UUID.randomUUID(), PAYMENT_CAPTURED, payload, paymentOccurredAt.plusSeconds(1));
        PaymentCapturedEvent paymentCapturedEvent = PaymentCapturedEvent.fromEventEnvelope(envelope);
        LedgerEntryDto ledgerEntryDto = PaymentCapturedEvent.toLedgerEntryDto(paymentCapturedEvent);
        doNothing().when(ledgerService).process(ledgerEntryDto);

        consumer.consume(envelope);

        verifyNoMoreInteractions(ledgerService);
    }

    @Test
    void consume_whenEnvelopeIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> consumer.consume(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a payment captured event");

        verifyNoMoreInteractions(ledgerService);
    }

    @Test
    void consume_whenEnvelopeTypeNotPaymentCaptured_throwsIllegalArgumentException() {
        EventEnvelope<PaymentEventPayload> eventEnvelope =
                new EventEnvelope<>(UUID.randomUUID(), null, null, Instant.now());

        assertThatThrownBy(() -> consumer.consume(eventEnvelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a payment captured event");

        verifyNoMoreInteractions(ledgerService);
    }
}
