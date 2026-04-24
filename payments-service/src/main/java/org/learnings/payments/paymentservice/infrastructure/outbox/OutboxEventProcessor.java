package org.learnings.payments.paymentservice.infrastructure.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OutboxEventProcessor {

    private static final int MAX_RETRIES = 5;

    private final OutboxRepository outboxRepository;
    private final OutboxEventSender outboxEventSender;

    public OutboxEventProcessor(OutboxRepository outboxRepository, OutboxEventSender outboxEventSender) {
        this.outboxRepository = outboxRepository;
        this.outboxEventSender = outboxEventSender;
    }

    @Transactional
    public void processPendingEvents() {
        List<OutboxEvent> events = outboxRepository.
                findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                System.out.println("Publishing event: " + event.getEventType());
                outboxEventSender.send(event);
                event.setPublished(true);
            } catch (Exception ex) {
                handleRetry(event, ex);
            }

            outboxRepository.save(event);
        }
    }

    private void handleRetry(OutboxEvent event, Exception ex) {
        int retry = event.getRetryCount() + 1;
        event.setRetryCount(retry);

        if (retry >= MAX_RETRIES) {
            event.setFailed(true);
            event.setLastError(ex.getMessage());
            return;
        }

        event.setNextRetryAt(Instant.now().plusSeconds(backoff(retry)));
    }

    private long backoff(int retry) {
        // 10s → 20s → 40s → 80s → ...
        return (long) Math.pow(2, retry) * 5;
    }
}
