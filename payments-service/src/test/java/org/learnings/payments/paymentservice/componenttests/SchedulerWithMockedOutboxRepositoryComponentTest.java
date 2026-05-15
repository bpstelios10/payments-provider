package org.learnings.payments.paymentservice.componenttests;

import org.junit.jupiter.api.Test;
import org.learnings.payments.messaging.outbox.OutboxRecord;
import org.learnings.payments.messaging.outbox.jpa.OutboxEvent;
import org.learnings.payments.messaging.outbox.jpa.OutboxEventProcessorRunner;
import org.learnings.payments.messaging.outbox.jpa.OutboxEventRepository;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.adapters.outbound.kafka.KafkaOutboxEventSender;
import org.learnings.payments.paymentservice.application.PaymentGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "outbox.schedule.delay=1000",
                "outbox.schedule.initialDelay=500"
        })
public class SchedulerWithMockedOutboxRepositoryComponentTest {

    @Value("${outbox.schedule.delay}") private int outboxScheduleDelay;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @MockitoBean
    private PaymentGateway paymentGateway;
    @MockitoBean
    private KafkaOutboxEventSender kafkaOutboxEventSender;
    @MockitoSpyBean
    private OutboxEventProcessorRunner outboxEventProcessorRunner;

    @Test
    void outboxScheduleDelay_is1000() {
        assertThat(outboxScheduleDelay).isEqualTo(1000);
    }

    @Test
    void scheduler_whenOutboxEventsExist_publishesThem() {
        OutboxEvent event1 = OutboxEvent.fromRecord(new OutboxRecord(1L, "PAYMENT", PaymentStatus.INITIATED.name(), "{}"));
        OutboxEvent event2 = OutboxEvent.fromRecord(new OutboxRecord(2L, "PAYMENT", PaymentStatus.INITIATED.name(), "{}"));
        outboxEventRepository.saveAll(List.of(event1, event2));

        await()
                .atMost(2, SECONDS)
                .pollInterval(1, SECONDS)
                .untilAsserted(() -> {
                    List<OutboxEvent> upForScheduler =
                            outboxEventRepository.findAndLockTop100ByPublishedFalseAndFailedFalseAndNextRetryAtBeforeOrderByCreatedAtAsc(
                                    Instant.now().truncatedTo(ChronoUnit.MILLIS));
                    assertThat(upForScheduler).isEmpty();
                });

        outboxEventRepository.findAll().forEach(e -> {
            assertThat(e.isPublished()).isTrue();
            assertThat(e.isFailed()).isFalse();
            assertThat(e.getAggregateId()).isIn(1L, 2L);
            assertThat(e.getAggregateType()).isEqualTo("PAYMENT");
            assertThat(e.getEventType()).isEqualTo(PaymentStatus.INITIATED.name());
            assertThat(e.getPayload()).isEqualTo("{}");
        });

        verifyMockInvocationsBetween(outboxEventProcessorRunner, "run", 1, 2);
    }

    @Test
    void scheduler_whenOutboxEventsExistButKafkaFails_publishesFails() {
        OutboxEvent event = OutboxEvent.fromRecord(new OutboxRecord(1L, "PAYMENT", PaymentStatus.INITIATED.name(), "{}"));
        OutboxEvent savedEvent = outboxEventRepository.save(event);
        doThrow(new RuntimeException("Kafka is down")).when(kafkaOutboxEventSender).send(any(OutboxRecord.class));

        await()
                .atMost(2, SECONDS)
                .pollInterval(1, SECONDS)
                .untilAsserted(() -> {
                    Optional<OutboxEvent> possiblyUpdated = outboxEventRepository.findById(savedEvent.getId());
                    assertThat(possiblyUpdated).isPresent();
                    assertThat(possiblyUpdated.get().getRetryCount()).isGreaterThanOrEqualTo(1);
                    assertThat(possiblyUpdated.get().isPublished()).isFalse();
                    assertThat(possiblyUpdated.get().isFailed()).isFalse();
                    assertThat(possiblyUpdated.get().getAggregateId()).isEqualTo(1L);
                    assertThat(possiblyUpdated.get().getAggregateType()).isEqualTo("PAYMENT");
                    assertThat(possiblyUpdated.get().getEventType()).isEqualTo(PaymentStatus.INITIATED.name());
                    assertThat(possiblyUpdated.get().getPayload()).isEqualTo("{}");
                });

        verifyMockInvocationsBetween(outboxEventProcessorRunner, "run", 1, 2);
    }

    @Test
    void scheduler_whenOutboxEventsExistButKafkaFailsAndMultipleRetries_onlyTwoRetriesIn12Seconds() {
        OutboxEvent event = OutboxEvent.fromRecord(new OutboxRecord(1L, "PAYMENT", PaymentStatus.INITIATED.name(), "{}"));
        OutboxEvent savedEvent = outboxEventRepository.save(event);
        UUID savedEventId = savedEvent.getId();
        doThrow(new RuntimeException("Kafka is down")).when(kafkaOutboxEventSender).send(any(OutboxRecord.class));

        await()
                .atMost(12, SECONDS)
                .pollInterval(1, SECONDS)
                .untilAsserted(() -> {
                    Optional<OutboxEvent> possiblyUpdated = outboxEventRepository.findById(savedEventId);
                    assertThat(possiblyUpdated).isPresent();
                    assertThat(possiblyUpdated.get().getAggregateId()).isEqualTo(1L);
                    assertThat(possiblyUpdated.get().getAggregateType()).isEqualTo("PAYMENT");
                    assertThat(possiblyUpdated.get().getEventType()).isEqualTo(PaymentStatus.INITIATED.name());
                    assertThat(possiblyUpdated.get().getPayload()).isEqualTo("{}");
                    assertThat(possiblyUpdated.get().isPublished()).isFalse();
                    assertThat(possiblyUpdated.get().getRetryCount()).isGreaterThanOrEqualTo(2);
                    assertThat(possiblyUpdated.get().isFailed()).isFalse();
                });

        verifyMockInvocationsBetween(outboxEventProcessorRunner, "run", 11, 12);
    }

    @SuppressWarnings("SameParameterValue")
    private void verifyMockInvocationsBetween(Object mock, String method, long atLeast, long atMost) {
        long invocations = mockingDetails(mock)
                .getInvocations()
                .stream()
                .filter(inv -> inv.getMethod().getName().equals(method))
                .count();

        assertThat(invocations).isBetween(atLeast, atMost);
    }
}
