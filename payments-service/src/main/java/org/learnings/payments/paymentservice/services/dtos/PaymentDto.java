package org.learnings.payments.paymentservice.services.dtos;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@EqualsAndHashCode
public final class PaymentDto {
    private final Long paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final String merchantId;
    private final UUID idempotencyKey;
    private final PaymentStatus status;
    private final Instant createdDate;
    private final Instant updatedDate;

    public PaymentDto(BigDecimal amount, String currency, String merchantId, UUID idempotencyKey, PaymentStatus status) {
        this(null, amount, currency, merchantId, idempotencyKey, status, null, null);
    }

    public PaymentDto(Long paymentId, BigDecimal amount, String currency, String merchantId,
                      UUID idempotencyKey, PaymentStatus status, Instant createdDate, Instant updatedDate) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public static Payment toPayment(PaymentDto paymentDto, PaymentStatus status) {
        return new Payment(paymentDto.amount, paymentDto.currency, paymentDto.merchantId, paymentDto.idempotencyKey, status);
    }

    public static PaymentDto fromPayment(Payment payment) {
        return new PaymentDto(payment.getPaymentId(), payment.getAmount(), payment.getCurrency(), payment.getMerchantId(),
                payment.getIdempotencyKey(), payment.getStatus(), payment.getCreatedDate(), payment.getUpdatedDate());
    }
}
