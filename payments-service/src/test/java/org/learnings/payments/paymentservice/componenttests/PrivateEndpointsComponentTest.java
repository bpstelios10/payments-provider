package org.learnings.payments.paymentservice.componenttests;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testing spring-web and spring-actuator endpoints
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("component-test-actuator")
public class PrivateEndpointsComponentTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getPrivateStatus() throws Exception {
        mockMvc.perform(get("/private/status"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("OK")));
    }

    @Test
    void getActuatorLinks() throws Exception {
        mockMvc.perform(get("/payments-service/private").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['_links']").isNotEmpty());
    }

    @Test
    void getActuatorMetrics() throws Exception {
        mockMvc.perform(get("/private/status"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/payments-service/private/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http_server_requests_seconds_count{error=\"none\",exception=\"none\",method=\"GET\",outcome=\"SUCCESS\",status=\"200\",uri=\"/private/status\"} ")));
    }

    @Test
    void getActuatorConfigProps() throws Exception {
        mockMvc.perform(get("/payments-service/private/configprops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contexts.application.beans").isNotEmpty());
    }

    @Test
    void getActuatorEnv() throws Exception {
        mockMvc.perform(get("/payments-service/private/env"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProfiles").value("component-test-actuator"));
    }

    @Test
    void getActuatorHeapdump() throws Exception {
        mockMvc.perform(get("/payments-service/private/heapdump"))
                .andExpect(status().isOk());
    }

    @Test
    void getActuatorHealth() throws Exception {
        mockMvc.perform(get("/payments-service/private/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['status']").value("UP"))
                .andExpect(content().string(containsString("liveness")))
                .andExpect(content().string(containsString("readiness")));
    }

    @Test
    void getActuatorLivenessCheck() throws Exception {
        mockMvc.perform(get("/payments-service/private/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['status']").value("UP"));
    }

    @Test
    void getActuatorReadinessCheck() throws Exception {
        mockMvc.perform(get("/payments-service/private/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['status']").value("UP"));
    }
}
