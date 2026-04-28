package org.learnings.payments.transactionsservice.services.dtos;

import org.learnings.payments.transactionsservice.domain.LedgerEntry;
import org.learnings.payments.transactionsservice.domain.LedgerType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record LedgerEntryDto(UUID eventId, Long paymentId, LedgerType type, BigDecimal amount,
                             String currency, UUID referenceId, Instant paymentOccurredAt) {

    public static LedgerEntry toLedgerEntryEntity(LedgerEntryDto ledgerEntryDto) {
        return new LedgerEntry(ledgerEntryDto.eventId(), ledgerEntryDto.paymentId(), ledgerEntryDto.type(),
                ledgerEntryDto.amount(), ledgerEntryDto.currency(), ledgerEntryDto.referenceId(),
                ledgerEntryDto.paymentOccurredAt().truncatedTo(ChronoUnit.MILLIS));
    }

    public static LedgerEntryDto fromLedgerEntryEntity(LedgerEntry ledgerEntry) {
        return new LedgerEntryDto(ledgerEntry.getEventId(), ledgerEntry.getPaymentId(), ledgerEntry.getType(),
                ledgerEntry.getAmount(), ledgerEntry.getCurrency(), ledgerEntry.getReferenceId(),
                ledgerEntry.getPaymentOccurredAt().truncatedTo(ChronoUnit.MILLIS));
    }
}
