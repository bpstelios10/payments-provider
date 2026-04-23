package org.learnings.payments.paymentservice.services.statustransitions;

import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ProcessPaymentActionStrategy implements PaymentActionStrategy {

    private static final Set<PaymentStatus> TRANSITION_ALLOWED_FROM =
            Set.of(PaymentStatus.INITIATED, PaymentStatus.PROCESSING);

    @Override
    public boolean supports(PaymentStatus currentStatus, PaymentStatusAction action) {
        return action == PaymentStatusAction.START_PROCESSING &&
                TRANSITION_ALLOWED_FROM.stream()
                        .anyMatch(entry -> entry.equals(currentStatus));
    }

    @Override
    public PaymentStatus getNextState() {
        return PaymentStatus.PROCESSING;
    }
}
