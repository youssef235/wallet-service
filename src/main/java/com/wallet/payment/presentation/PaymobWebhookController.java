package com.wallet.payment.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.wallet.payment.application.HandlePaymobWebhookUseCase;
import com.wallet.payment.application.PaymobTransactionCallback;
import com.wallet.payment.application.WebhookProcessingResult;
import com.wallet.payment.infrastructure.paymob.PaymobHmacVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Paymob "transaction processed" callbacks. Public endpoint (Paymob
 * servers call it), so authenticity comes from HMAC verification, not login.
 * Always answers 200 for processed/duplicate callbacks so Paymob stops retrying;
 * answers 401 when the HMAC does not verify (nothing is written in that case).
 */
@RestController
@RequestMapping("/api/webhooks/paymob")
class PaymobWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymobWebhookController.class);

    private final PaymobHmacVerifier hmacVerifier;
    private final HandlePaymobWebhookUseCase handlePaymobWebhookUseCase;

    PaymobWebhookController(PaymobHmacVerifier hmacVerifier,
                            HandlePaymobWebhookUseCase handlePaymobWebhookUseCase) {
        this.hmacVerifier = hmacVerifier;
        this.handlePaymobWebhookUseCase = handlePaymobWebhookUseCase;
    }

    @PostMapping
    ResponseEntity<Void> transactionCallback(@RequestParam(name = "hmac", required = false) String hmac,
                                             @RequestBody JsonNode body) {
        JsonNode obj = body == null ? null : body.get("obj");
        if (obj == null || !hmacVerifier.verify(obj, hmac)) {
            // Fail closed: an unverified callback never changes state.
            log.warn("Rejected Paymob callback: HMAC verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String merchantOrderId = obj.path("order").path("merchant_order_id").asText(null);
        if (merchantOrderId == null || !obj.hasNonNull("id")) {
            log.warn("Rejected Paymob callback: missing transaction id or merchant order id");
            return ResponseEntity.badRequest().build();
        }

        var callback = new PaymobTransactionCallback(
                obj.get("id").asLong(),
                merchantOrderId,
                obj.path("amount_cents").asLong(),
                obj.path("success").asBoolean(false));

        WebhookProcessingResult result = handlePaymobWebhookUseCase.execute(callback);
        if (result == WebhookProcessingResult.UNKNOWN_ORDER) {
            // Still 200: retrying will not make the order known, and we logged it.
            log.warn("Paymob callback referenced unknown order {}", merchantOrderId);
        }
        return ResponseEntity.ok().build();
    }
}
