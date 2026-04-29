package org.learnings.payments.transactionsservice.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnings.payments.transactionsservice.domain.LedgerEntry;
import org.learnings.payments.transactionsservice.domain.LedgerType;
import org.learnings.payments.transactionsservice.repositories.LedgerEntryRepository;
import org.learnings.payments.transactionsservice.services.dtos.LedgerEntryDto;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @InjectMocks
    private LedgerServiceImpl ledgerService;

    @Test
    void process_whenSaveSucceeds_savesLedgerEntry() {
        LedgerEntryDto dto = new LedgerEntryDto(UUID.randomUUID(), 123L, LedgerType.CAPTURED,
                BigDecimal.valueOf(100.00), "USD", null, Instant.now());
        LedgerEntry entity = LedgerEntryDto.toLedgerEntryEntity(dto);
        when(ledgerEntryRepository.save(entity)).thenReturn(entity);

        ledgerService.process(dto);

        verifyNoMoreInteractions(ledgerEntryRepository);
    }

    @Test
    void process_whenDataIntegrityViolationAndEntryFound_logsAndContinues() {
        LedgerEntryDto dto = new LedgerEntryDto(UUID.randomUUID(), 123L, LedgerType.CAPTURED,
                BigDecimal.valueOf(100.00), "USD", null, Instant.now());
        LedgerEntry entity = LedgerEntryDto.toLedgerEntryEntity(dto);
        LedgerEntry existing = new LedgerEntry(dto.eventId(), dto.paymentId(), dto.type(), dto.amount(),
                dto.currency(), dto.referenceId(), dto.paymentOccurredAt());
        when(ledgerEntryRepository.save(entity)).thenThrow(DataIntegrityViolationException.class);
        when(ledgerEntryRepository.findByEventId(dto.eventId())).thenReturn(Optional.of(existing));

        ledgerService.process(dto);

        verifyNoMoreInteractions(ledgerEntryRepository);
    }

    @Test
    void process_whenDataIntegrityViolationAndEntryNotFound_throwsException() {
        LedgerEntryDto dto = new LedgerEntryDto(UUID.randomUUID(), 123L, LedgerType.CAPTURED,
                BigDecimal.valueOf(100.00), "USD", null, Instant.now());
        LedgerEntry entity = LedgerEntryDto.toLedgerEntryEntity(dto);
        when(ledgerEntryRepository.save(entity)).thenThrow(new DataIntegrityViolationException("oops"));
        when(ledgerEntryRepository.findByEventId(dto.eventId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.process(dto))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("oops");

        verifyNoMoreInteractions(ledgerEntryRepository);
    }

    @Test
    void process_whenOtherException_throwsException() {
        LedgerEntryDto dto = new LedgerEntryDto(UUID.randomUUID(), 123L, LedgerType.CAPTURED,
                BigDecimal.valueOf(100.00), "USD", null, Instant.now());
        LedgerEntry entity = LedgerEntryDto.toLedgerEntryEntity(dto);
        when(ledgerEntryRepository.save(entity)).thenThrow(new RuntimeException("oops"));

        assertThatThrownBy(() -> ledgerService.process(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("oops");

        verifyNoMoreInteractions(ledgerEntryRepository);
    }
}
