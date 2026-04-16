package org.learnings.payments.paymentservice.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "UNIQUE_IDEMTOTENCY_KEY", columnNames = {"idempotencyKey"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@EqualsAndHashCode
public class Payment {

    public Payment(BigDecimal amount, String currency, String merchantId, UUID idempotencyKey, String status) {
        this.amount = amount;
        this.currency = currency;
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;
    private String currency;
    private String merchantId;
    private UUID idempotencyKey;
    private String status;
    @CreationTimestamp
    private Instant createdDate;
    @UpdateTimestamp
    private Instant updatedDate;
}
