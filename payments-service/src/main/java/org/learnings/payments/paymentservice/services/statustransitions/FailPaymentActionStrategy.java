package org.learnings.payments.paymentservice.services.statustransitions;

import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class FailPaymentActionStrategy implements PaymentActionStrategy {

    private static final Set<PaymentStatus> TRANSITION_ALLOWED_FROM = Set.of(PaymentStatus.PROCESSING);

    @Override
    public boolean supports(PaymentStatus currentStatus, PaymentStatusAction action) {
        return action == PaymentStatusAction.FAIL &&
                TRANSITION_ALLOWED_FROM.stream()
                        .anyMatch(entry -> entry == currentStatus);
    }

    @Override
    public PaymentStatus getNextState() {
        return PaymentStatus.FAILED;
    }
}
