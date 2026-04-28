package org.learnings.payments.transactionsservice;

import org.learnings.payments.transactionsservice.adapters.messaging.config.KafkaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@EnableConfigurationProperties(KafkaProperties.class)
@SpringBootApplication
public class TransactionsServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(TransactionsServiceApplication.class, args);
    }
}
