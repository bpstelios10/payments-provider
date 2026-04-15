package org.learnings.payments.paymentservice.componenttests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.web.controllers.PaymentsController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public class PaymentsErrorsComponentTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private TestPaymentRepository repository;

    @Test
    void createPayment_succeeds() throws Exception {
        UUID idempotencyId = UUID.randomUUID();
        PaymentsController.CreatePayment requestBody =
                new PaymentsController.CreatePayment(BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId);

        MvcResult mvcResult = mockMvc.perform(
                        post("/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(requestBody)))
                .andReturn();

        String contentAsString = mvcResult.getResponse().getContentAsString();
        Payment byPaymentId = repository.findByPaymentId(Long.parseLong(contentAsString));

        assertThat("merch-1").isEqualTo(byPaymentId.getMerchantId());
    }

    @ParameterizedTest
    @MethodSource("badRequestsProvider")
    @NullSource
    void createPayment_whenInvalidInput_throwsBadRequest(PaymentsController.CreatePayment request) throws Exception {
        mockMvc.perform(
                        post("/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    public static Stream<Arguments> badRequestsProvider() {
        UUID idempotencyId = UUID.randomUUID();
        return Stream.of(
                // validate amount
                Arguments.of(new PaymentsController.CreatePayment(null, "USD", "merch-1", idempotencyId)),
                Arguments.of(new PaymentsController.CreatePayment(BigDecimal.valueOf(-10.2), "USD", "merch-1", idempotencyId)),
                Arguments.of(new PaymentsController.CreatePayment(BigDecimal.valueOf(1.222), "USD", "merch-1", idempotencyId)),
                // validate merchant-id
                Arguments.of(new PaymentsController.CreatePayment(BigDecimal.valueOf(10.2), null, null, idempotencyId)),
                Arguments.of(new PaymentsController.CreatePayment(BigDecimal.valueOf(10.2), null, "", idempotencyId)),
                // validate idempotency-id
                Arguments.of(new PaymentsController.CreatePayment(BigDecimal.valueOf(10.2), null, "merch-1", null))
        );
    }
}
