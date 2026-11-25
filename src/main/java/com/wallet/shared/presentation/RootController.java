package com.wallet.shared.presentation;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Root liveness endpoint. Platform health checks and load balancers probe GET /
 * and expect a 2xx; without it the request falls through to the static resource
 * handler and surfaces as a 500 via NoResourceFoundException.
 */
@RestController
class RootController {

    @GetMapping("/")
    ResponseEntity<Map<String, String>> root() {
        return ResponseEntity.ok(Map.of(
                "service", "wallet-service",
                "status", "ok",
                "docs", "/swagger-ui/index.html"));
    }
}
