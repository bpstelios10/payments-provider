package org.learnings.payments.messaging.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock
    private OutboxEventSender sender;

    @Test
    void process_whenSendSucceeds_marksRecordPublished() {
        OutboxRecord record1 = new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}");
        OutboxRecord record2 = new OutboxRecord(2L, "PAYMENT", "INITIATED", "{}");
        OutboxProcessor processor = new OutboxProcessor(sender);

        processor.process(List.of(record1, record2));

        assertThat(record1.isPublished()).isTrue();
        assertThat(record2.isPublished()).isTrue();
        assertThat(record1.getRetryCount()).isEqualTo(0);
        assertThat(record2.getRetryCount()).isEqualTo(0);
        verify(sender).send(record1);
        verify(sender).send(record2);
        verifyNoMoreInteractions(sender);
    }

    @Test
    void process_whenNoRecords_noInteractions() {
        OutboxProcessor processor = new OutboxProcessor(sender);

        processor.process(List.of());

        verifyNoMoreInteractions(sender);
    }

    @Test
    void process_whenSendFails_incrementsRetryCountAndSchedulesRetry() {
        OutboxRecord record1 = new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}");
        OutboxRecord record2 = new OutboxRecord(2L, "PAYMENT", "INITIATED", "{}");
        doNothing().when(sender).send(record1);
        doThrow(new RuntimeException("send failed")).when(sender).send(record2);
        OutboxProcessor processor = new OutboxProcessor(sender);

        processor.process(List.of(record1, record2));

        assertThat(record1.isPublished()).isTrue();
        assertThat(record1.getRetryCount()).isEqualTo(0);
        assertThat(record2.isPublished()).isFalse();
        assertThat(record2.getRetryCount()).isEqualTo(1);
        assertThat(record2.getNextRetryAt()).isAfter(Instant.now().minusSeconds(1));
        verifyNoMoreInteractions(sender);
    }

    @Test
    void process_whenSendFailsRepeatedly_marksFailedAfterMaxRetries() {
        OutboxRecord record = new OutboxRecord(1L, "PAYMENT", "INITIATED", "{}");
        doThrow(new RuntimeException("send failed")).when(sender).send(record);
        OutboxProcessor processor = new OutboxProcessor(sender);
        int retryCount = 0;
        Instant previousRetryAt = Instant.now().minusSeconds(5);

        while (!record.isFailed()) {
            processor.process(List.of(record));

            assertThat(record.isPublished()).isFalse();
            assertThat(record.getRetryCount()).isEqualTo(++retryCount);
            if (retryCount < 5) {
                assertThat(record.getNextRetryAt()).isAfter(previousRetryAt);
                previousRetryAt = record.getNextRetryAt();
            }
        }

        assertThat(record.getRetryCount()).isEqualTo(5);
        assertThat(record.isFailed()).isTrue();
        assertThat(record.getLastError()).isEqualTo("send failed");
        verify(sender, times(5)).send(record);
        verifyNoMoreInteractions(sender);
    }
}
