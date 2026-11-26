package com.wallet.payment.presentation;

import tools.jackson.databind.JsonNode;
import com.wallet.payment.application.HandlePaymobReversalUseCase;
import com.wallet.payment.application.HandlePaymobWebhookUseCase;
import com.wallet.payment.application.ReversalProcessingResult;
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
    private final HandlePaymobReversalUseCase handlePaymobReversalUseCase;

    PaymobWebhookController(PaymobHmacVerifier hmacVerifier,
                            HandlePaymobWebhookUseCase handlePaymobWebhookUseCase,
                            HandlePaymobReversalUseCase handlePaymobReversalUseCase) {
        this.hmacVerifier = hmacVerifier;
        this.handlePaymobWebhookUseCase = handlePaymobWebhookUseCase;
        this.handlePaymobReversalUseCase = handlePaymobReversalUseCase;
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

        boolean isRefunded = obj.path("is_refunded").asBoolean(false);
        boolean isVoided = obj.path("is_voided").asBoolean(false);
        if (isRefunded || isVoided) {
            // A reversal: the provider is taking a settled payment back. Paymob sends a
            // new transaction id for the refund, linked to the original via
            // parent_transaction; a void arrives on the original transaction itself.
            // We bind by order.id, which the HMAC covers; parent_transaction is only
            // a fallback because it is not signed.
            long reversalTransactionId = obj.get("id").asLong();
            Long paymobOrderId = obj.path("order").hasNonNull("id")
                    ? obj.path("order").get("id").asLong() : null;
            long originalTransactionId = obj.path("parent_transaction").asLong(reversalTransactionId);
            ReversalProcessingResult reversal = handlePaymobReversalUseCase.execute(
                    reversalTransactionId, paymobOrderId, originalTransactionId,
                    obj.path("amount_cents").asLong());
            if (reversal == ReversalProcessingResult.UNKNOWN_TRANSACTION) {
                log.error("Paymob reversal {} referenced unknown original transaction {}",
                        reversalTransactionId, originalTransactionId);
            }
            return ResponseEntity.ok().build();
        }

        var callback = new PaymobTransactionCallback(
                obj.get("id").asLong(),
                merchantOrderId,
                obj.path("amount_cents").asLong(),
                obj.path("currency").asText(""),
                obj.path("success").asBoolean(false),
                obj.path("pending").asBoolean(false),
                obj.path("error_occured").asBoolean(false),
                obj.path("is_voided").asBoolean(false),
                obj.path("is_refunded").asBoolean(false));

        WebhookProcessingResult result = handlePaymobWebhookUseCase.execute(callback);
        if (result == WebhookProcessingResult.UNKNOWN_ORDER) {
            // Still 200: retrying will not make the order known, and we logged it.
            log.warn("Paymob callback referenced unknown order {}", merchantOrderId);
        }
        return ResponseEntity.ok().build();
    }
}
