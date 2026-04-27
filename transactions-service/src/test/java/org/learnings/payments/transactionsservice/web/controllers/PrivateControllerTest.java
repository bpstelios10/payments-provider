package org.learnings.payments.transactionsservice.web.controllers;

import org.junit.jupiter.api.Test;
import org.learnings.payments.transactionsservice.adapters.controllers.web.PrivateController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateControllerTest {

    private final PrivateController controller = new PrivateController();

    @Test
    void status() {
        ResponseEntity<String> response = controller.status();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat("OK").isEqualTo(response.getBody());
    }
}
