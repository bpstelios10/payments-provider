package org.learnings.payments.paymentservice.componenttests;

import org.learnings.payments.paymentservice.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestPaymentRepository extends JpaRepository<Payment, Long> {

    Payment findByPaymentId(Long paymentId);
}
