package org.learnings.payments.transactionsservice.adapters.messaging.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentEventPayload(
        Long paymentId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) { }
