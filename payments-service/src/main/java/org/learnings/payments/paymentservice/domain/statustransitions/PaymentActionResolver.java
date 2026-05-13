package org.learnings.payments.paymentservice.domain.statustransitions;

import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PaymentActionResolver {

    private final List<PaymentActionStrategy> paymentActionStrategies;

    public PaymentActionResolver(List<PaymentActionStrategy> paymentActionStrategies) {
        this.paymentActionStrategies = paymentActionStrategies;
    }

    public Optional<PaymentStatus> getNextStatus(PaymentStatus currentStatus, PaymentStatusAction nextAction) {
        Optional<PaymentActionStrategy> paymentActionStrategy = paymentActionStrategies.stream()
                .filter(s -> s.supports(currentStatus, nextAction))
                .findFirst();

        return paymentActionStrategy.map(PaymentActionStrategy::getNextState);
    }
}
