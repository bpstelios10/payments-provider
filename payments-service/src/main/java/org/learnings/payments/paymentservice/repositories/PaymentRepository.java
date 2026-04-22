package org.learnings.payments.paymentservice.repositories;

import jakarta.annotation.Nonnull;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Override
    <S extends Payment> S save(S entity);

    @Nonnull
    @Override
    Optional<Payment> findById(Long paymentId);

    Optional<Payment> findByIdempotencyKey(UUID idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update Payment p
            set p.status = :newStatus
            where p.paymentId = :paymentId and p.status = :currentStatus""")
    int setStatusIfCurrentStatusIs(long paymentId, PaymentStatus newStatus, PaymentStatus currentStatus);
}
