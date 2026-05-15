package org.learnings.payments.messaging.outbox.jpa;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the outbox processing pipeline.
 *
 * <p>Fires {@link OutboxEventProcessorRunner#run()} at a configurable fixed delay.
 * The scheduler is only activated when {@code outbox.spring.scheduling.enabled=true}
 * is present in the application properties, allowing it to be disabled in tests or
 * environments where an external trigger is preferred.
 *
 * <p><b>Required configuration properties:</b>
 * <pre>{@code
 * outbox.spring.scheduling.enabled=true
 * outbox.schedule.delay=5000          # ms between runs (after previous run completes)
 * outbox.schedule.initialDelay=10000  # ms to wait before the first run
 * }</pre>
 *
 * <p><b>Note:</b> the consuming application must have {@code @EnableScheduling} on one
 * of its configuration classes for this scheduler to activate.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "outbox.spring.scheduling.enabled", havingValue = "true")
public class OutboxScheduler {

    private final OutboxEventProcessorRunner runner;

    /**
     * Creates a new scheduler backed by the given runner.
     *
     * @param runner the runner that fetches and processes pending outbox events
     */
    public OutboxScheduler(OutboxEventProcessorRunner runner) {
        this.runner = runner;
    }

    /**
     * Triggers a processing run on the configured fixed delay schedule.
     */
    @Scheduled(fixedDelayString = "${outbox.schedule.delay}", initialDelayString = "${outbox.schedule.initialDelay}")
    public void process() {
        runner.run();
    }
}
