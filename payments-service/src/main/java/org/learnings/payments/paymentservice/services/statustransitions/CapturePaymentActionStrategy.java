package org.learnings.payments.paymentservice.services.statustransitions;

import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CapturePaymentActionStrategy implements PaymentActionStrategy {

    private static final Set<PaymentStatus> TRANSITION_ALLOWED_FROM = Set.of(PaymentStatus.PROCESSING);

    @Override
    public boolean supports(PaymentStatus currentStatus, PaymentStatusAction action) {
        return action == PaymentStatusAction.CAPTURE &&
                TRANSITION_ALLOWED_FROM.stream()
                        .anyMatch(entry -> entry == currentStatus);
    }

    @Override
    public PaymentStatus getNextState() {
        return PaymentStatus.CAPTURED;
    }
}
