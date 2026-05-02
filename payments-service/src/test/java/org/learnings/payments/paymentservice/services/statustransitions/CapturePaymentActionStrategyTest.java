package org.learnings.payments.paymentservice.services.statustransitions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.domain.PaymentStatusAction;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CapturePaymentActionStrategyTest {

    private final CapturePaymentActionStrategy strategy = new CapturePaymentActionStrategy();

    @ParameterizedTest
    @MethodSource("supportsCasesProvider")
    void supports(PaymentStatus currentStatus, PaymentStatusAction action, boolean expectedResult) {
        boolean result = strategy.supports(currentStatus, action);

        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void getNextState() {
        PaymentStatus nextState = strategy.getNextState();

        assertThat(nextState).isEqualTo(PaymentStatus.CAPTURED);
    }

    public static Stream<Arguments> supportsCasesProvider() {
        return Stream.of(
                Arguments.of(PaymentStatus.PROCESSING, PaymentStatusAction.CAPTURE, true),
                Arguments.of(PaymentStatus.FAILED, PaymentStatusAction.CAPTURE, false),
                Arguments.of(PaymentStatus.PROCESSING, PaymentStatusAction.CREATE, false)
        );
    }
}
