package org.learnings.payments.transactionsservice.adapters.web.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateControllerTest {

    private final PrivateController controller = new PrivateController();

    @Test
    void status_whenCalled_returnsOkResponse() {
        ResponseEntity<String> response = controller.status();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat("OK").isEqualTo(response.getBody());
    }
}
