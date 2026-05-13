package org.learnings.payments.transactionsservice.application;

import org.learnings.payments.transactionsservice.application.dtos.LedgerEntryDto;

public interface LedgerService {
    void process(LedgerEntryDto ledgerEntryDto);
}
