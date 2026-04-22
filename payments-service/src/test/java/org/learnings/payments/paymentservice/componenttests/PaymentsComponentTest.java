package org.learnings.payments.paymentservice.componenttests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.domain.PaymentStatus;
import org.learnings.payments.paymentservice.services.PaymentGateway;
import org.learnings.payments.paymentservice.web.controllers.PaymentsController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    @MockitoBean
    private PaymentGateway paymentGateway;

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
        PaymentsController.PaymentResponse paymentResponseDto =
                jsonMapper.readValue(contentAsString, PaymentsController.PaymentResponse.class);
        assertThat(paymentResponseDto).isNotNull();
        assertThat(paymentResponseDto.status()).isEqualTo(PaymentStatus.INITIATED);

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
        Payment payment = new Payment(
                BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId, PaymentStatus.INITIATED);

        Payment savedPayment = repository.save(payment);
        long paymentId = savedPayment.getPaymentId();

        MvcResult mvcResultExecute = mockMvc.perform(post("/payments/{paymentId}/execute", paymentId))
                .andExpect(status().isOk())
                .andReturn();
        String contentAsString = mvcResultExecute.getResponse().getContentAsString();
        PaymentsController.PaymentResponse paymentResponseDto =
                jsonMapper.readValue(contentAsString, PaymentsController.PaymentResponse.class);
        assertThat(paymentResponseDto).isNotNull();
        assertThat(paymentResponseDto.status()).isEqualTo(PaymentStatus.CAPTURED);

        Payment byPaymentId = repository.findByPaymentId(paymentId);
        assertThat("merch-1").isEqualTo(byPaymentId.getMerchantId());
        assertThat(PaymentStatus.INITIATED).isNotEqualTo(byPaymentId.getStatus());
        assertThat(byPaymentId.getCreatedDate()).isNotEqualTo(byPaymentId.getUpdatedDate());
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    void executePayment_whenRaceCondition_shouldTriggerLockException() throws ExecutionException, InterruptedException {
        UUID idempotencyId = UUID.randomUUID();
        Payment payment = new Payment(BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId, PaymentStatus.INITIATED);

        Payment savedPayment = repository.save(payment);
        long paymentId = savedPayment.getPaymentId();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // mock payment gateway in a way to make sure that both threads will reach this point at the same time
            // this we way we enforce the race condition will 100% happens
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            doAnswer(_ -> {
                ready.countDown();                          // signal arrival
                start.await(1, TimeUnit.SECONDS);  // wait for release
                return null;
            }).when(paymentGateway).executePayment(any(), eq(idempotencyId));
            CompletableFuture<MvcResult> firstExecute = CompletableFuture.supplyAsync(performExecute(paymentId), executor);
            CompletableFuture<MvcResult> secondExecute = CompletableFuture.supplyAsync(performExecute(paymentId), executor);

            // wait until BOTH threads reached the mock
            ready.await(1, TimeUnit.SECONDS);
            // release them at the same time
            start.countDown();

            List<MvcResult> resultActions = firstExecute.thenCombine(secondExecute, List::of).get();
            List<Integer> resultCodes = resultActions.stream().map(e -> e.getResponse().getStatus()).toList();
            List<String> resultMessages = resultActions.stream().map(e -> {
                try {
                    return e.getResponse().getContentAsString();
                } catch (UnsupportedEncodingException ex) {
                    throw new RuntimeException(ex);
                }
            }).toList();

            assertThat(resultCodes).containsExactlyInAnyOrder(200, 409);
            assertThat(resultMessages).containsExactlyInAnyOrder(
                    "{\"paymentId\":" + savedPayment.getPaymentId() + ",\"status\":\"CAPTURED\"}", "");
        }
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

    private Supplier<MvcResult> performExecute(long paymentId) {
        return () -> {
            try {
                return mockMvc.perform(post("/payments/{paymentId}/execute", paymentId)).andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
