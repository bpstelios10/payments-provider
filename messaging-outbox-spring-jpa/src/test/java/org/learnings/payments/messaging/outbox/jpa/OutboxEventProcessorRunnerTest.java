package org.learnings.payments.messaging.outbox.jpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnings.payments.messaging.outbox.OutboxProcessor;
import org.learnings.payments.messaging.outbox.OutboxRecord;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorRunnerTest {

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private OutboxProcessor processor;
    @InjectMocks
    private OutboxEventProcessorRunner runner;

    @Test
    void run_whenEventsExist_delegatesToProcessorAndSyncsStateBack() {
        OutboxEvent event1 = OutboxEvent.fromRecord(new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}"));
        OutboxEvent event2 = OutboxEvent.fromRecord(new OutboxRecord(2L, "PAYMENT", "INITIATED", "{}"));
        when(repository.findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(Instant.class))).thenReturn(List.of(event1, event2));
        doAnswer(invocation -> {
            List<OutboxRecord> records = invocation.getArgument(0);
            records.forEach(r -> r.setPublished(true));
            return null;
        }).when(processor).process(anyList());

        runner.run();

        assertThat(event1.isPublished()).isTrue();
        assertThat(event2.isPublished()).isTrue();
        verify(processor).process(argThat(records -> records.size() == 2));
        verifyNoMoreInteractions(processor);
    }

    @Test
    void run_whenNoEvents_callsProcessorWithEmptyList() {
        when(repository.findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(Instant.class))).thenReturn(List.of());

        runner.run();

        verify(processor).process(List.of());
        verifyNoMoreInteractions(processor);
    }

    @Test
    void run_whenProcessorSetsRetry_syncsRetryStateBackToEntity() {
        OutboxEvent event = OutboxEvent.fromRecord(new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}"));
        Instant nextRetryAt = Instant.now().plusSeconds(10);
        when(repository.findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(Instant.class))).thenReturn(List.of(event));
        doAnswer(invocation -> {
            List<OutboxRecord> records = invocation.getArgument(0);
            records.getFirst().setRetryCount(1);
            records.getFirst().setNextRetryAt(nextRetryAt);
            return null;
        }).when(processor).process(anyList());

        runner.run();

        assertThat(event.isPublished()).isFalse();
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextRetryAt()).isEqualTo(nextRetryAt);
    }

    @Test
    void run_whenProcessorMarksFailed_syncsFailedStateBackToEntity() {
        OutboxEvent event = OutboxEvent.fromRecord(new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}"));
        when(repository.findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(Instant.class))).thenReturn(List.of(event));
        doAnswer(invocation -> {
            List<OutboxRecord> records = invocation.getArgument(0);
            records.getFirst().setFailed(true);
            records.getFirst().setLastError("max retries exceeded");
            return null;
        }).when(processor).process(anyList());

        runner.run();

        assertThat(event.isFailed()).isTrue();
        assertThat(event.getLastError()).isEqualTo("max retries exceeded");
    }
}
