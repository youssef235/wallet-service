package com.wallet.payment.infrastructure.paymob;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies the HMAC of a Paymob transaction-processed callback.
 *
 * Paymob concatenates a fixed list of fields from the callback's `obj` payload
 * (in their documented order, with no separator) and computes HMAC-SHA512 with
 * the merchant's HMAC secret; the result arrives as the `hmac` query parameter.
 * We recompute it and compare in constant time. A mismatch must always fail closed.
 */
@Component
public class PaymobHmacVerifier {

    private final String hmacSecret;

    public PaymobHmacVerifier(@Value("${app.paymob.hmac-secret:}") String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public boolean isConfigured() {
        return hmacSecret != null && !hmacSecret.isBlank();
    }

    public boolean verify(JsonNode obj, String providedHmac) {
        if (!isConfigured() || obj == null || providedHmac == null || providedHmac.isBlank()) {
            return false;
        }
        String computed = computeHex(concatenate(obj));
        byte[] a = computed.getBytes(StandardCharsets.UTF_8);
        byte[] b = providedHmac.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /** Field order documented by Paymob for the transaction processed callback. */
    static String concatenate(JsonNode obj) {
        StringBuilder sb = new StringBuilder();
        append(sb, obj, "amount_cents");
        append(sb, obj, "created_at");
        append(sb, obj, "currency");
        append(sb, obj, "error_occured");
        append(sb, obj, "has_parent_transaction");
        append(sb, obj, "id");
        append(sb, obj, "integration_id");
        append(sb, obj, "is_3d_secure");
        append(sb, obj, "is_auth");
        append(sb, obj, "is_capture");
        append(sb, obj, "is_refunded");
        append(sb, obj, "is_standalone_payment");
        append(sb, obj, "is_voided");
        appendNested(sb, obj, "order", "id");
        append(sb, obj, "owner");
        append(sb, obj, "pending");
        appendNested(sb, obj, "source_data", "pan");
        appendNested(sb, obj, "source_data", "sub_type");
        appendNested(sb, obj, "source_data", "type");
        append(sb, obj, "success");
        return sb.toString();
    }

    private static void append(StringBuilder sb, JsonNode obj, String field) {
        JsonNode node = obj.get(field);
        sb.append(node == null || node.isNull() ? "" : node.asText());
    }

    private static void appendNested(StringBuilder sb, JsonNode obj, String parent, String field) {
        JsonNode parentNode = obj.get(parent);
        if (parentNode == null || parentNode.isNull()) {
            return;
        }
        JsonNode node = parentNode.get(field);
        sb.append(node == null || node.isNull() ? "" : node.asText());
    }

    private String computeHex(String concatenated) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(concatenated.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }
}
