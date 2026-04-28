package org.learnings.payments.transactionsservice.componenttests;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.learnings.payments.transactionsservice.adapters.messaging.model.EventEnvelope;
import org.learnings.payments.transactionsservice.adapters.messaging.model.EventType;
import org.learnings.payments.transactionsservice.adapters.messaging.model.PaymentEventPayload;
import org.learnings.payments.transactionsservice.domain.LedgerEntry;
import org.learnings.payments.transactionsservice.domain.LedgerType;
import org.learnings.payments.transactionsservice.repositories.LedgerEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1,
        topics = {"PAYMENT_CAPTURED"},
        brokerProperties = {
        "log.dirs=${java.io.tmpdir}/kafka-${random.uuid}",
        // try to reduce the memory needed by embedded-kafka
                "log.segment.bytes=1048576",
        "log.segment.bytes=1048576", // 1MB instead of 1GB
        "log.retention.bytes=1048576",
        "num.network.threads=2",     // Reduce thread overhead
        "num.io.threads=2"})
@TestPropertySource(properties = {
        "kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.learnings.payments.transactionsservice.adapters.messaging.model.EventEnvelopeSerializer"
})
class LedgerEntryComponentTest {

    @Autowired
    private KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @BeforeAll
    static void setup(@Autowired KafkaListenerEndpointRegistry registry,
                      @Autowired EmbeddedKafkaBroker embeddedKafkaBroker) {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
        }
    }

    private static final CountDownLatch latch = new CountDownLatch(1);
    private static EventEnvelope<?> received;

    @Test
    void shouldConsumeEventSerializedWithCorrectFormat() throws Exception {
        UUID eventId = UUID.randomUUID();
        Instant paymentOccurredAt = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MILLIS);
        EventEnvelope<PaymentEventPayload> event =
                new EventEnvelope<>(
                        eventId,
                        EventType.PAYMENT_CAPTURED,
                        new PaymentEventPayload(
                                123L,
                                new BigDecimal("42.50"),
                                "EUR",
                                paymentOccurredAt
                        ),
                        Instant.now().truncatedTo(ChronoUnit.MILLIS)
                );

        kafkaTemplate.send(new ProducerRecord<>("PAYMENT_CAPTURED", event));
        boolean consumed = latch.await(3, TimeUnit.SECONDS);

        assertThat(consumed).isTrue();
        assertThat(received).isNotNull();
        assertThat(received.eventType()).isEqualTo(EventType.PAYMENT_CAPTURED);

        PaymentEventPayload payload = (PaymentEventPayload) received.payload();
        assertThat(payload.paymentId()).isEqualTo(123L);
        assertThat(payload.amount()).isEqualByComparingTo("42.50");
        assertThat(payload.currency()).isEqualTo("EUR");
        assertThat(received.occurredAt()).isNotNull();
        assertThat(payload.occurredAt()).isNotNull();

        await()
                .atMost(3, SECONDS)
                .pollInterval(1, SECONDS)
                .untilAsserted(() -> {
                    Optional<LedgerEntry> savedLedgerEntry = ledgerEntryRepository.findByEventId(eventId);
                    assertThat(savedLedgerEntry).isPresent();
                    assertThat(savedLedgerEntry.get().getType()).isEqualTo(LedgerType.CAPTURED);
                    assertThat(savedLedgerEntry.get().getPaymentOccurredAt()).isEqualTo(paymentOccurredAt);
                    assertThat(savedLedgerEntry.get().getCreatedAt()).isAfter(event.occurredAt());
                    assertThat(savedLedgerEntry.get().getCreatedAt()).isAfter(paymentOccurredAt);
                });
    }

    // test consumer
    @SuppressWarnings("UnusedDeclaration")
    @KafkaListener(groupId = "test-group", topics = "PAYMENT_CAPTURED")
    void listen(EventEnvelope<?> event) {
        received = event;
        latch.countDown();
    }
}
