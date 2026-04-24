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
import java.time.Duration;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
        assertThat(PaymentStatus.CAPTURED).isEqualTo(byPaymentId.getStatus());
        assertThat(byPaymentId.getCreatedDate()).isNotEqualTo(byPaymentId.getUpdatedDate());
    }

    @Test
    void executePayment_whenGatewayFails_succeedsWithFailedStatus() throws Exception {
        UUID idempotencyId = UUID.randomUUID();
        Payment payment = new Payment(
                BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId, PaymentStatus.INITIATED);

        Payment savedPayment = repository.save(payment);
        long paymentId = savedPayment.getPaymentId();

        doThrow(new RuntimeException("gateway error")).when(paymentGateway).executePayment(any(), eq(idempotencyId));

        MvcResult mvcResultExecute = mockMvc.perform(post("/payments/{paymentId}/execute", paymentId))
                .andExpect(status().isOk())
                .andReturn();
        String contentAsString = mvcResultExecute.getResponse().getContentAsString();
        PaymentsController.PaymentResponse paymentResponseDto =
                jsonMapper.readValue(contentAsString, PaymentsController.PaymentResponse.class);
        assertThat(paymentResponseDto).isNotNull();
        assertThat(paymentResponseDto.status()).isEqualTo(PaymentStatus.FAILED);

        Payment byPaymentId = repository.findByPaymentId(paymentId);
        assertThat("merch-1").isEqualTo(byPaymentId.getMerchantId());
        assertThat(PaymentStatus.FAILED).isEqualTo(byPaymentId.getStatus());
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

    /*
     * This test uses 3 threads to make the same request. we know that only 1 should enter the 'Processing' status
     * So we use a count-down-latch and we tell the threads 'when you finish execution, countdown'. We also put the
     * latch.wait in the mocked payment-gateway call. So the 1 thread that enters processing status, waits till the 2
     * other threads return quickly (with 409 status, lock-exception). and then it continues.
     * In the end we verify the response codes are 200, 409 and 409
     */
    @Test
    void executePayment_whenRaceCondition_shouldTriggerLockException() throws ExecutionException, InterruptedException {
        UUID idempotencyId = UUID.randomUUID();
        Payment payment = new Payment(BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId, PaymentStatus.INITIATED);
        List<MvcResult> resultActions;

        // create the payment in the DB
        Payment savedPayment = repository.save(payment);
        long paymentId = savedPayment.getPaymentId();

        // set up the payment gateway mock to handle the latch
        CountDownLatch lockedOutThread = new CountDownLatch(2);
        doAnswer(_ -> {
            System.out.println("##### [" + Thread.currentThread().getName() + "] - inside payment-gateway");
            // wait till the locked-out threads finish
            boolean isLockCountDownReached = lockedOutThread.await(1, TimeUnit.SECONDS);
            assertThat(isLockCountDownReached).isTrue();
            System.out.println("##### [" + Thread.currentThread().getName() + "] - continue after payment-gateway");
            return null;
        }).when(paymentGateway).executePayment(any(), eq(idempotencyId));

        // run the threads in parallel and block for results
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            CompletableFuture<MvcResult> firstExecute = CompletableFuture.supplyAsync(performExecute(paymentId, lockedOutThread), executor);
            CompletableFuture<MvcResult> secondExecute = CompletableFuture.supplyAsync(performExecute(paymentId, lockedOutThread), executor);
            CompletableFuture<MvcResult> thirdExecute = CompletableFuture.supplyAsync(performExecute(paymentId, lockedOutThread), executor);

            System.out.println("##### [" + Thread.currentThread().getName() + "] - before blocking the futures");
            CompletableFuture<Void> all = CompletableFuture.allOf(firstExecute, secondExecute, thirdExecute);
            all.get();
            resultActions = List.of(firstExecute.get(), secondExecute.get(), thirdExecute.get());
        }

        // assert responses
        List<Integer> resultCodes = resultActions.stream().map(e -> e.getResponse().getStatus()).toList();
        List<String> resultMessages = resultActions.stream().map(e -> {
            try {
                return e.getResponse().getContentAsString();
            } catch (UnsupportedEncodingException ex) {
                throw new RuntimeException(ex);
            }
        }).toList();
        assertThat(resultCodes).containsExactlyInAnyOrder(200, 409, 409);
        assertThat(resultMessages).containsExactlyInAnyOrder(
                "{\"paymentId\":" + savedPayment.getPaymentId() + ",\"status\":\"CAPTURED\"}", "", "");

        // assert actual database impact
        Payment capturedPayment = repository.findByPaymentId(paymentId);
        assertThat(PaymentStatus.CAPTURED).isEqualTo(capturedPayment.getStatus());
        assertThat(capturedPayment.getCreatedDate()).isBefore(capturedPayment.getProcessingStartedAt());
        assertThat(capturedPayment.getProcessingStartedAt()).isBefore(capturedPayment.getUpdatedDate());
    }

    @Test
    void executePayment_whenProcessingThreadFailsAndTimeoutPeriodPasses_thenNextThreadFinishesProcessing() throws Exception {
        UUID idempotencyId = UUID.randomUUID();
        Payment payment = new Payment(BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId, PaymentStatus.INITIATED);

        // create the payment in the DB
        Payment savedPayment = repository.save(payment);
        long paymentId = savedPayment.getPaymentId();

        // first attempt should fail, but second should pass
        doThrow(new ServiceConfigurationError("boom"))
                .doNothing()
                .when(paymentGateway)
                .executePayment(any(), eq(idempotencyId));

        // FIRST ATTEMPT
        mockMvc.perform(post("/payments/{paymentId}/execute", paymentId))
                .andExpect(status().isInternalServerError());

        // assert actual database impact
        Payment capturedPayment = repository.findByPaymentId(paymentId);
        assertThat(PaymentStatus.PROCESSING).isEqualTo(capturedPayment.getStatus());
        assertThat(capturedPayment.getProcessingStartedAt()).isNotNull();
        assertThat(capturedPayment.getCreatedDate()).isBefore(capturedPayment.getProcessingStartedAt());

        // wait for processing timeout to pass
        sleep(Duration.ofSeconds(11)); // TODO make this configurable for tests

        // SECOND ATTEMPT
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

    private Supplier<MvcResult> performExecute(long paymentId, CountDownLatch lockedOutThread) {
        return () -> {
            try {
                System.out.println("##### [" + Thread.currentThread().getName() + "] - starting");
                MvcResult mvcResult = mockMvc.perform(post("/payments/{paymentId}/execute", paymentId)).andReturn();
                // count down only after return. so the locked-out threads that fail-fast will be taken into account
                lockedOutThread.countDown();

                return mvcResult;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
