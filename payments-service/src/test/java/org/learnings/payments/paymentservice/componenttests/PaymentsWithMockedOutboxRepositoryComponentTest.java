package org.learnings.payments.paymentservice.componenttests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.learnings.payments.paymentservice.domain.Payment;
import org.learnings.payments.paymentservice.infrastructure.outbox.OutboxEvent;
import org.learnings.payments.paymentservice.infrastructure.outbox.OutboxRepository;
import org.learnings.payments.paymentservice.services.PaymentGateway;
import org.learnings.payments.paymentservice.web.controllers.PaymentsController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("component-test")
@AutoConfigureMockMvc
public class PaymentsWithMockedOutboxRepositoryComponentTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private TestPaymentRepository repository;
    @MockitoBean
    private OutboxRepository outboxRepository;
    @MockitoBean
    private PaymentGateway paymentGateway;

    @BeforeEach
    void setup() {
        doThrow(new CannotGetJdbcConnectionException("oops"))
                .when(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void createPayment_whenFailsToPublishEvent_doesNotCreatePayment() throws Exception {
        UUID idempotencyId = UUID.randomUUID();
        PaymentsController.CreatePayment requestBody =
                new PaymentsController.CreatePayment(BigDecimal.valueOf(10.2), "USD", "merch-1", idempotencyId);

        MvcResult mvcResult = mockMvc.perform(
                        post("/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(requestBody)))
                .andExpect(status().isInternalServerError())
                .andReturn();

        assertThat(mvcResult).isNotNull();
        assertThat(mvcResult.getResponse()).isNotNull();
        String contentAsString = mvcResult.getResponse().getContentAsString();
        assertThat(contentAsString).isEmpty();

        List<Payment> allPayments = repository.findAll();
        assertThat(allPayments).hasSize(0);
    }
}
