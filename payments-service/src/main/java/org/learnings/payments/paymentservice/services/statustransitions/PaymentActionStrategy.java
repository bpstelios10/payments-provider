package org.learnings.payments.paymentservice.services.statustransitions;

import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;

public interface PaymentActionStrategy {

    boolean supports(PaymentStatus currentStatus, PaymentStatusAction action);

    PaymentStatus getNextState();
}
