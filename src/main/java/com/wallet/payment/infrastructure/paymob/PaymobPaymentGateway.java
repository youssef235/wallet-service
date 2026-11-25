package com.wallet.payment.infrastructure.paymob;

import com.fasterxml.jackson.databind.JsonNode;
import com.wallet.payment.domain.PaymentGateway;
import com.wallet.payment.domain.PaymentGatewayException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Paymob Accept integration, classic 3-step flow:
 * 1. authenticate (api key -> auth token)
 * 2. register the order (-> Paymob order id)
 * 3. request a payment key and build the hosted iframe URL the user pays through.
 */
@Component
@ConditionalOnProperty(name = "app.paymob.enabled", havingValue = "true")
class PaymobPaymentGateway implements PaymentGateway {

    private final RestClient http;
    private final PaymobProperties properties;

    PaymobPaymentGateway(RestClient.Builder builder, PaymobProperties properties) {
        this.http = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public PaymentIntent createPaymentIntent(Long topUpId, String merchantOrderId, long amountMinor,
                                             String currency, String customerEmail, String customerFullName) {
        try {
            String authToken = authenticate();
            long orderId = registerOrder(authToken, merchantOrderId, amountMinor, currency);
            String paymentKey = requestPaymentKey(authToken, orderId, amountMinor, currency,
                    customerEmail, customerFullName);
            String paymentUrl = properties.baseUrl() + "/api/acceptance/iframes/" + properties.iframeId()
                    + "?payment_token=" + paymentKey;
            return new PaymentIntent(orderId, paymentUrl);
        } catch (RestClientException | IllegalStateException ex) {
            throw new PaymentGatewayException("Failed to create Paymob payment: " + ex.getMessage(), ex);
        }
    }

    private String authenticate() {
        JsonNode response = http.post()
                .uri("/api/auth/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("api_key", properties.apiKey()))
                .retrieve()
                .body(JsonNode.class);
        return requiredText(response, "token");
    }

    private long registerOrder(String authToken, String merchantOrderId, long amountMinor, String currency) {
        Map<String, Object> body = new HashMap<>();
        body.put("auth_token", authToken);
        body.put("delivery_needed", false);
        body.put("amount_cents", amountMinor);
        body.put("currency", currency);
        body.put("merchant_order_id", merchantOrderId);
        body.put("items", List.of());
        JsonNode response = http.post()
                .uri("/api/ecommerce/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return requiredLong(response, "id");
    }

    private String requestPaymentKey(String authToken, long orderId, long amountMinor, String currency,
                                     String customerEmail, String customerFullName) {
        String[] names = splitName(customerFullName);
        Map<String, Object> billing = new HashMap<>();
        billing.put("first_name", names[0]);
        billing.put("last_name", names[1]);
        billing.put("email", customerEmail);
        billing.put("phone_number", "NA");
        billing.put("apartment", "NA");
        billing.put("floor", "NA");
        billing.put("street", "NA");
        billing.put("building", "NA");
        billing.put("city", "NA");
        billing.put("country", "EG");
        billing.put("state", "NA");
        billing.put("shipping_method", "NA");
        billing.put("postal_code", "NA");

        Map<String, Object> body = new HashMap<>();
        body.put("auth_token", authToken);
        body.put("amount_cents", amountMinor);
        body.put("expiration", 3600);
        body.put("order_id", orderId);
        body.put("billing_data", billing);
        body.put("currency", currency);
        body.put("integration_id", properties.integrationId());

        JsonNode response = http.post()
                .uri("/api/acceptance/payment_keys")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return requiredText(response, "token");
    }

    private static String[] splitName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) {
            return new String[]{trimmed.isEmpty() ? "Customer" : trimmed, "-"};
        }
        return new String[]{trimmed.substring(0, space), trimmed.substring(space + 1)};
    }

    private static String requiredText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            throw new IllegalStateException("Paymob response missing field: " + field);
        }
        return node.get(field).asText();
    }

    private static long requiredLong(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            throw new IllegalStateException("Paymob response missing field: " + field);
        }
        return node.get(field).asLong();
    }
}
