package org.learnings.payments.paymentservice.repositories;

import jakarta.annotation.Nonnull;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
            UPDATE Payment p
            SET p.status = org.learnings.payments.paymentservice.domain.PaymentStatus.PROCESSING,
                p.processingStartedAt = :now,
                p.updatedDate = :now
            WHERE p.paymentId = :paymentId
            AND (
                p.status = org.learnings.payments.paymentservice.domain.PaymentStatus.INITIATED
                OR (
                    p.status = org.learnings.payments.paymentservice.domain.PaymentStatus.PROCESSING
                    AND p.processingStartedAt < :timeout
                )
            )""")
    // if we store the processingStartedAt with CURRENT_TIMESTAMP and then compare with Instant, it always fails
    int claimProcessingStatus(long paymentId, Instant now, Instant timeout);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Payment p
            SET p.status = :newStatus,
                p.updatedDate = CURRENT_TIMESTAMP
            WHERE p.paymentId = :paymentId AND p.status = :currentStatus""")
    int setStatusIfCurrentStatusIs(long paymentId, PaymentStatus newStatus, PaymentStatus currentStatus);
}
