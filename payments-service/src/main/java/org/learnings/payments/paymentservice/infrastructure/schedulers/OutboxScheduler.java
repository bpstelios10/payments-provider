package org.learnings.payments.paymentservice.infrastructure.schedulers;

import org.learnings.payments.paymentservice.infrastructure.outbox.OutboxEventProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * This is the Outbox Pattern Scheduler
 * It is a separate class to separate the scheduling/timing part from the outbox logic
 */
@Component
public class OutboxScheduler {

    private final OutboxEventProcessor processor;

    public OutboxScheduler(OutboxEventProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelay = 5000)
    public void process() {
        processor.processPendingEvents();
    }
}
