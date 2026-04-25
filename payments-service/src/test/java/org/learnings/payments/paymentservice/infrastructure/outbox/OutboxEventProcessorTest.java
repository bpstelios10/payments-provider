package org.learnings.payments.paymentservice.infrastructure.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private OutboxEventSender outboxEventSender;
    @InjectMocks
    private OutboxEventProcessor outboxEventProcessor;

    @Test
    void processPendingEvents_succeeds() {
        OutboxEvent event1 = new OutboxEvent(1L, "PAYMENT", "PAYMENT_STATUS", "{}");
        OutboxEvent event2 = new OutboxEvent(2L, "PAYMENT", "PAYMENT_STATUS", "{}");
        when(outboxRepository.
                findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of(event1, event2));

        outboxEventProcessor.processPendingEvents();

        assertThat(event1.isPublished()).isTrue();
        assertThat(event2.isPublished()).isTrue();
        assertThat(event1.getRetryCount()).isEqualTo(0);
        assertThat(event2.getRetryCount()).isEqualTo(0);
        verify(outboxRepository).save(event1);
        verify(outboxRepository).save(event2);
        verify(outboxEventSender).send(event1);
        verify(outboxEventSender).send(event2);
        verifyNoMoreInteractions(outboxRepository, outboxEventSender);
    }

    @Test
    void publishEvents_whenNoEvents_noProcessing() {
        when(outboxRepository.
                findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of());

        outboxEventProcessor.processPendingEvents();

        verifyNoMoreInteractions(outboxRepository, outboxEventSender);
    }

    @Test
    void publishEvents_whenEventSenderFails_setRetries() {
        OutboxEvent event1 = new OutboxEvent(1L, "PAYMENT", "PAYMENT_STATUS", "{}");
        OutboxEvent event2 = new OutboxEvent(2L, "PAYMENT", "PAYMENT_STATUS", "{}");
        when(outboxRepository.
                findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of(event1, event2));
        doNothing().when(outboxEventSender).send(event1);
        doThrow(new RuntimeException("Failed to send event2")).when(outboxEventSender).send(event2);

        outboxEventProcessor.processPendingEvents();

        assertThat(event1.isPublished()).isTrue();
        assertThat(event2.isPublished()).isFalse();
        assertThat(event1.getRetryCount()).isEqualTo(0);
        assertThat(event2.getRetryCount()).isEqualTo(1);
        verify(outboxRepository).save(event1);
        verify(outboxRepository).save(event2);
        verifyNoMoreInteractions(outboxRepository, outboxEventSender);
    }

    @Test
    void publishEvents_whenEventSenderFailsMultipleTimes_setRetriesSucceeds() {
        OutboxEvent event1 = new OutboxEvent(1L, "PAYMENT", "PAYMENT_STATUS", "{}");
        when(outboxRepository.
                findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of(event1));
        doThrow(new RuntimeException("Failed to send event1")).when(outboxEventSender).send(event1);
        int retryCount = 0;
        Instant nextRetryAfter = Instant.now().minusSeconds(5);

        while (!event1.isFailed()) {
            outboxEventProcessor.processPendingEvents();

            assertThat(event1.isPublished()).isFalse();
            assertThat(event1.getRetryCount()).isEqualTo(++retryCount);
            if (retryCount != 5) {
                assertThat(event1.getNextRetryAt()).isAfter(nextRetryAfter);
                nextRetryAfter = event1.getNextRetryAt();
            }
        }

        assertThat(event1.isPublished()).isFalse();
        assertThat(event1.getRetryCount()).isEqualTo(5);
        verify(outboxRepository, times(5)).save(event1);
        verifyNoMoreInteractions(outboxRepository, outboxEventSender);
    }
}
