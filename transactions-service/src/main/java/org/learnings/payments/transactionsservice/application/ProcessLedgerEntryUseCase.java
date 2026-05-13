package org.learnings.payments.transactionsservice.application;

import lombok.extern.slf4j.Slf4j;
import org.learnings.payments.transactionsservice.domain.LedgerEntry;
import org.learnings.payments.transactionsservice.domain.repositories.LedgerEntryRepository;
import org.learnings.payments.transactionsservice.application.dtos.LedgerEntryDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class ProcessLedgerEntryUseCase {

    private final LedgerEntryRepository ledgerEntryRepository;

    public ProcessLedgerEntryUseCase(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public void execute(LedgerEntryDto ledgerEntryDto) {
        log.debug("Processing ledger entry [{}]", ledgerEntryDto);
        LedgerEntry ledgerEntryEntity = LedgerEntryDto.toLedgerEntryEntity(ledgerEntryDto);

        try {
            ledgerEntryRepository.save(ledgerEntryEntity);
        } catch (DataIntegrityViolationException dae) {
            log.debug("ledger-entry creation failed with error: [{}]", dae.getMessage());
            Optional<LedgerEntry> byIdempotencyKey = ledgerEntryRepository.findByEventId(ledgerEntryDto.eventId());
            LedgerEntryDto existing = LedgerEntryDto.fromLedgerEntryEntity(byIdempotencyKey.orElseThrow(() -> dae));
            log.debug("ledger-entry found for event-id [{}]", existing.eventId());
        } catch (Exception ex) {
            log.error("Show me the error: [{}]", ex.getMessage());

            throw ex;
        }
    }
}
