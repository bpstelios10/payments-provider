package org.learnings.payments.paymentservice.componenttests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.services.PaymentResponseDto;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public class PaymentsComponentTest {

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

        assertThat(mvcResult).isNotNull();
        assertThat(mvcResult.getResponse()).isNotNull();
        String contentAsString = mvcResult.getResponse().getContentAsString();
        PaymentResponseDto paymentResponseDto = jsonMapper.readValue(contentAsString, PaymentResponseDto.class);
        assertThat(paymentResponseDto).isNotNull();
        assertThat(paymentResponseDto.status()).isEqualTo("pending");

        Payment byPaymentId = repository.findByPaymentId(paymentResponseDto.paymentId());
        assertThat("merch-1").isEqualTo(byPaymentId.getMerchantId());
    }

    @Test
    void createPayment_whenSameIdempotencyKey_avoidsRetryByReturningExistingPayment() throws Exception {
        UUID idempotencyId = UUID.randomUUID();
        PaymentsController.CreatePayment requestBody =
                new PaymentsController.CreatePayment(BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId);

        mockMvc.perform(
                        post("/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());


        mockMvc.perform(
                        post("/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk());
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

    @Test
    void executePayment_succeeds() throws Exception {
        UUID idempotencyId = UUID.randomUUID();
        PaymentsController.CreatePayment requestBody =
                new PaymentsController.CreatePayment(BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId);

        MvcResult mvcResult = mockMvc.perform(
                        post("/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn();
        String contentAsString = mvcResult.getResponse().getContentAsString();
        PaymentResponseDto paymentResponseDto = jsonMapper.readValue(contentAsString, PaymentResponseDto.class);
        assertThat(paymentResponseDto).isNotNull();
        assertThat(paymentResponseDto.status()).isEqualTo("pending");

        Long createdPaymentId = paymentResponseDto.paymentId();

        MvcResult mvcResultExecute = mockMvc.perform(post("/payments/{paymentId}/execute", createdPaymentId))
                .andExpect(status().isOk())
                .andReturn();
        contentAsString = mvcResultExecute.getResponse().getContentAsString();
        paymentResponseDto = jsonMapper.readValue(contentAsString, PaymentResponseDto.class);
        assertThat(paymentResponseDto).isNotNull();
        assertThat(paymentResponseDto.status()).isEqualTo("executed");

        Payment byPaymentId = repository.findByPaymentId(createdPaymentId);
        assertThat("merch-1").isEqualTo(byPaymentId.getMerchantId());
        assertThat("executed").isEqualTo(byPaymentId.getStatus());
        assertThat(byPaymentId.getCreatedDate()).isNotEqualTo(byPaymentId.getUpdatedDate());
        assertThat(byPaymentId.getVersion()).isGreaterThan(0);
    }

    @Test
    void executePayment_whenNotExistedPayment_throwsNotFound() throws Exception {
        mockMvc.perform(post("/payments/{paymentId}/execute", 185723482357L))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"'',404", "' ',400"})
    void executePayment_whenInvalidInput_throwsBadRequest(String paymentId, int errorStatusCode) throws Exception {
        mockMvc.perform(post("/payments/{paymentId}/execute", paymentId))
                .andExpect(status().is(errorStatusCode));
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
