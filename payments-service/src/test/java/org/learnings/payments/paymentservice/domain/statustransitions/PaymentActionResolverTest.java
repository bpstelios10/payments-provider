package org.learnings.payments.paymentservice.domain.statustransitions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.learnings.payments.paymentservice.domain.PaymentStatus.INITIATED;
import static org.learnings.payments.paymentservice.domain.PaymentStatus.PROCESSING;
import static org.learnings.payments.paymentservice.domain.PaymentStatusAction.START_PROCESSING;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentActionResolverTest {

    @Mock
    private PaymentActionStrategy strategy;
    @Mock
    private PaymentActionStrategy otherStrategy;

    @Test
    void getNextStatus_whenStrategySupports_returnsNextStatus() {
        when(strategy.supports(INITIATED, START_PROCESSING)).thenReturn(true);
        when(strategy.getNextState()).thenReturn(PROCESSING);
        PaymentActionResolver resolver = new PaymentActionResolver(List.of(strategy));

        Optional<PaymentStatus> result = resolver.getNextStatus(INITIATED, START_PROCESSING);

        assertThat(result).contains(PROCESSING);
    }

    @Test
    void getNextStatus_whenNoStrategySupports_returnsEmpty() {
        when(strategy.supports(INITIATED, START_PROCESSING)).thenReturn(false);
        PaymentActionResolver resolver = new PaymentActionResolver(List.of(strategy));

        Optional<PaymentStatus> result = resolver.getNextStatus(INITIATED, START_PROCESSING);

        assertThat(result).isEmpty();
    }

    @Test
    void getNextStatus_whenMultipleStrategies_returnsFirstMatch() {
        when(strategy.supports(INITIATED, START_PROCESSING)).thenReturn(false);
        when(otherStrategy.supports(INITIATED, START_PROCESSING)).thenReturn(true);
        when(otherStrategy.getNextState()).thenReturn(PROCESSING);
        PaymentActionResolver resolver = new PaymentActionResolver(List.of(strategy, otherStrategy));

        Optional<PaymentStatus> result = resolver.getNextStatus(INITIATED, START_PROCESSING);

        assertThat(result).contains(PROCESSING);
    }
}
