package org.learnings.payments.paymentservice;

import org.learnings.payments.messaging.outbox.jpa.OutboxEventProcessorRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackageClasses = {PaymentsServiceApplication.class, OutboxEventProcessorRunner.class})
public class PaymentsServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(PaymentsServiceApplication.class, args);
    }
}
