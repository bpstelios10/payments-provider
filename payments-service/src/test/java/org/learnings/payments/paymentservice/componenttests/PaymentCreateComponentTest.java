package org.learnings.payments.paymentservice.componenttests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.learnings.payments.messaging.outbox.jpa.OutboxEventRepository;
import org.learnings.payments.paymentservice.adapters.inbound.controllers.PaymentsController;
import org.learnings.payments.paymentservice.application.PaymentGateway;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("component-test")
@AutoConfigureMockMvc
public class PaymentCreateComponentTest extends PaymentsComponentTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private TestPaymentRepository repository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @MockitoBean
    private PaymentGateway paymentGateway;

    @BeforeEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void createPayment_succeeds() throws Exception {
        UUID idempotencyId = UUID.randomUUID();
        PaymentsController.CreatePayment requestBody =
                new PaymentsController.CreatePayment(BigDecimal.valueOf(10.21), "USD", "merch-1", idempotencyId);

        MvcResult mvcResult = mockMvc.perform(
                        post("/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(mvcResult).isNotNull();
        assertThat(mvcResult.getResponse()).isNotNull();
        String contentAsString = mvcResult.getResponse().getContentAsString();
        PaymentsController.PaymentResponse paymentResponseDto =
                jsonMapper.readValue(contentAsString, PaymentsController.PaymentResponse.class);
        assertThat(paymentResponseDto).isNotNull();
        assertThat(paymentResponseDto.status()).isEqualTo(PaymentStatus.INITIATED);

        Payment byPaymentId = repository.findByPaymentId(paymentResponseDto.paymentId());
        assertThat("merch-1").isEqualTo(byPaymentId.getMerchantId());
        assertThatPaymentsAndOutboxTablesHaveNewRecords(1);
        assertThatOutboxEventIsCreated(byPaymentId);
    }

    @Test
    void createPayment_whenSameIdempotencyKey_avoidsRetryByReturningExistingPayment() throws Exception {
        UUID idempotencyId = UUID.randomUUID();
        PaymentsController.CreatePayment requestBody =
                new PaymentsController.CreatePayment(BigDecimal.valueOf(10.21), "USD", "merch-1", idempotencyId);

        MvcResult mvcResult1 = mockMvc.perform(
                        post("/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn();


        MvcResult mvcResult2 = mockMvc.perform(
                        post("/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(mvcResult1.getResponse().getContentAsString()).isEqualTo(mvcResult2.getResponse().getContentAsString());
        assertThatPaymentsAndOutboxTablesHaveNewRecords(1);
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

        assertThatPaymentsAndOutboxTablesHaveNewRecords(0);
    }

    private static Stream<Arguments> badRequestsProvider() {
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
