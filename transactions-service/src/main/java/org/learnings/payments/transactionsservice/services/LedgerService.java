package org.learnings.payments.transactionsservice.services;

import org.learnings.payments.transactionsservice.services.dtos.LedgerEntryDto;

public interface LedgerService {
    void process(LedgerEntryDto ledgerEntryDto);
}
