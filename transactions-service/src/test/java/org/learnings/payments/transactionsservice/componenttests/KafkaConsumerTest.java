package org.learnings.payments.transactionsservice.componenttests;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learnings.payments.transactionsservice.adapters.messaging.model.EventEnvelope;
import org.learnings.payments.transactionsservice.adapters.messaging.model.EventType;
import org.learnings.payments.transactionsservice.adapters.messaging.model.PaymentEventPayload;
import org.learnings.payments.transactionsservice.domain.LedgerEntry;
import org.learnings.payments.transactionsservice.domain.LedgerType;
import org.learnings.payments.transactionsservice.repositories.LedgerEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class KafkaConsumerTest {

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private KafkaProperties properties;
    private KafkaTemplate<String, String> stringKafkaTemplate;

    @BeforeEach
    void setupRawTemplate(@Autowired KafkaListenerEndpointRegistry registry,
                          @Autowired EmbeddedKafkaBroker embeddedKafkaBroker) {
        Map<String, Object> props = properties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(props);
        this.stringKafkaTemplate = new KafkaTemplate<>(factory);

        // Wait for the listener to be ready before the test starts
        registry.getListenerContainers().forEach(container ->
                ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic())
        );
    }

    @AfterEach
    void tearDown() {
        // Optional: clears the internal producer cache
        ((DefaultKafkaProducerFactory<?, ?>) stringKafkaTemplate.getProducerFactory()).destroy();
    }

    private static final CountDownLatch latch = new CountDownLatch(1);
    private static EventEnvelope<?> received;

    @Test
    void shouldConsumeEventAsStringWithCorrectFormat() throws Exception {
        // To test the raw string message, we need to create the Message object, so that we resolve the Envelope generic
        UUID eventId = UUID.randomUUID();
        Instant paymentOccurredAt = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MILLIS);
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String rawJson = """
                {
                  "eventId": "%s",
                  "eventType": "PAYMENT_CAPTURED",
                  "payload": {
                    "@type": "%s",
                    "paymentId": 123,
                    "amount": 42.50,
                    "currency": "EUR",
                    "occurredAt": "%s"
                  },
                  "occurredAt": "%s"
                }
                """.formatted(eventId, PaymentEventPayload.class.getName(), paymentOccurredAt, occurredAt);

        stringKafkaTemplate.send("PAYMENT_CAPTURED", rawJson);
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
                    assertThat(savedLedgerEntry.get().getCreatedAt()).isAfter(occurredAt);
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
