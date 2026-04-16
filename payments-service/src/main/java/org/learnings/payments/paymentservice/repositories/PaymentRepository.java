package org.learnings.payments.paymentservice.repositories;

import org.learnings.payments.paymentservice.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Override
    <S extends Payment> S save(S entity);

    Optional<Payment> findByIdempotencyKey(UUID idempotencyKey);
}
