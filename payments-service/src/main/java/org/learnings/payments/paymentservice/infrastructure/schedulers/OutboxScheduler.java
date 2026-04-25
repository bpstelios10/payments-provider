package org.learnings.payments.paymentservice.infrastructure.schedulers;

import org.learnings.payments.paymentservice.infrastructure.outbox.OutboxEventProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * This is the Outbox Pattern Scheduler
 * It is a separate class to separate the scheduling/timing part from the outbox logic
 */
@Component
@ConditionalOnProperty(name = "outbox.spring.scheduling.enabled", havingValue = "true")
public class OutboxScheduler {

    private final OutboxEventProcessor processor;

    public OutboxScheduler(OutboxEventProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${outbox.schedule.delay}", initialDelayString = "${outbox.schedule.initialDelay}")
    public void process() {
        processor.processPendingEvents();
    }
}
