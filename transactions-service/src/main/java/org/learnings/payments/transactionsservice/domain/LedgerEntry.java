package org.learnings.payments.transactionsservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = LedgerEntry.UNIQUE_LEDGER_ENTRY_IDEMPOTENCY_KEY, columnNames = {"eventId"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@EqualsAndHashCode
public class LedgerEntry {

    public static final String UNIQUE_LEDGER_ENTRY_IDEMPOTENCY_KEY = "UNIQUE_LEDGER_ENTRY_IDEMPOTENCY_KEY";

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID eventId;
    @Column(nullable = false)
    private Long paymentId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerType type;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    private UUID referenceId;
    @SuppressWarnings({"UnusedDeclaration"})
    @CreationTimestamp
    private Instant createdAt;
}
