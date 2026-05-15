package org.learnings.payments.messaging.outbox.jpa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnings.payments.messaging.outbox.OutboxEventSender;
import org.learnings.payments.messaging.outbox.OutboxRecord;
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
    private OutboxEventSender sender;
    private OutboxEventProcessorRunner runner;

    @BeforeEach
    void setUp() {
        runner = new OutboxEventProcessorRunner(repository, sender);
    }

    @Test
    void run_whenEventsExist_sendsThemAndSyncsPublishedStateBack() {
        OutboxEvent event1 = OutboxEvent.fromRecord(new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}"));
        OutboxEvent event2 = OutboxEvent.fromRecord(new OutboxRecord(2L, "PAYMENT", "INITIATED", "{}"));
        when(repository.findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(Instant.class))).thenReturn(List.of(event1, event2));

        runner.run();

        assertThat(event1.isPublished()).isTrue();
        assertThat(event2.isPublished()).isTrue();
        verify(sender).send(argThat(r -> r.getAggregateId().equals(1L)));
        verify(sender).send(argThat(r -> r.getAggregateId().equals(2L)));
        verifyNoMoreInteractions(sender);
    }

    @Test
    void run_whenNoEvents_senderNotCalled() {
        when(repository.findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(Instant.class))).thenReturn(List.of());

        runner.run();

        verifyNoMoreInteractions(sender);
    }

    @Test
    void run_whenSendFails_syncsRetryStateBackToEntity() {
        OutboxEvent event = OutboxEvent.fromRecord(new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}"));
        when(repository.findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(Instant.class))).thenReturn(List.of(event));
        doThrow(new RuntimeException("send failed")).when(sender).send(any());

        runner.run();

        assertThat(event.isPublished()).isFalse();
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextRetryAt()).isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    void run_whenSendFailsRepeatedly_syncsFailedStateBackToEntity() {
        OutboxEvent event = OutboxEvent.fromRecord(new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}"));
        when(repository.findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(Instant.class))).thenReturn(List.of(event));
        doThrow(new RuntimeException("send failed")).when(sender).send(any());

        for (int i = 0; i < 5; i++) {
            runner.run();
        }

        assertThat(event.isFailed()).isTrue();
        assertThat(event.getLastError()).isEqualTo("send failed");
        assertThat(event.isPublished()).isFalse();
    }
}
