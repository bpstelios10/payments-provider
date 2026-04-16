package org.learnings.payments.paymentservice.repositories;

import org.learnings.payments.paymentservice.domain.Payment;
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

    @Override
    Optional<Payment> findById(Long paymentId);

    Optional<Payment> findByIdempotencyKey(UUID idempotencyKey);

    @Modifying
    @Transactional
    @Query("""
            update Payment p
            set p.status = :status, p.version = p.version+1
            where p.paymentId = :paymentId and p.version = :version""")
    int updateIfVersionMatches(long paymentId, long version, String status);
}
