package org.learnings.payments.transactionsservice.adapters.web.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/private")
public class PrivateController {

    @GetMapping(path = "/status")
    public ResponseEntity<String> status() {
        return ResponseEntity.ok("OK");
    }
}
