package org.learnings.payments.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PaymentsServiceApplication {

    static void main(String[] args) {
        SpringApplication.run(PaymentsServiceApplication.class, args);
    }
}
