package com.wallet.payment.infrastructure.paymob;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Uses the worked example from Paymob's own HMAC documentation: the same
 * transaction payload, concatenated in the documented field order, must
 * reproduce the HMAC for the test secret.
 */
class PaymobHmacVerifierTest {

    private static final String SECRET = "test-hmac-secret";
    // HMAC-SHA512 of the documented concatenated string with key "test-hmac-secret".
    private static final String EXPECTED =
            "506662408b52167ce7bee5fb0135c0ca107a100fe600bdec92441f860cfb71cd6dd927ad8e7675a7cbb5ddec0976d59f98cfeaeb9b3453ddbf0ce1ea871d2507";

    private final PaymobHmacVerifier verifier = new PaymobHmacVerifier(SECRET);
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode sampleTransaction() throws Exception {
        String json = """
                {
                  "amount_cents": 100,
                  "created_at": "2020-03-25T18:39:44.719228",
                  "currency": "EGP",
                  "error_occured": false,
                  "has_parent_transaction": false,
                  "id": 2556706,
                  "integration_id": 6741,
                  "is_3d_secure": true,
                  "is_auth": false,
                  "is_capture": false,
                  "is_refunded": false,
                  "is_standalone_payment": true,
                  "is_voided": false,
                  "order": {"id": 4778239, "merchant_order_id": "WTU-1-abc"},
                  "owner": 4705,
                  "pending": false,
                  "source_data": {"pan": "2346", "sub_type": "MasterCard", "type": "card"},
                  "success": true
                }
                """;
        return mapper.readTree(json);
    }

    @Test
    void concatenationMatchesPaymobsDocumentedOrder() throws Exception {
        assertThat(PaymobHmacVerifier.concatenate(sampleTransaction()))
                .isEqualTo("1002020-03-25T18:39:44.719228EGPfalsefalse25567066741truefalsefalsefalse"
                        + "truefalse47782394705false2346MasterCardcardtrue");
    }

    @Test
    void correctHmacVerifies() throws Exception {
        assertThat(verifier.verify(sampleTransaction(), EXPECTED)).isTrue();
    }

    @Test
    void tamperedPayloadFailsClosed() throws Exception {
        JsonNode tampered = mapper.readTree(sampleTransaction().toString().replace("100", "100000"));
        assertThat(verifier.verify(tampered, EXPECTED)).isFalse();
    }

    @Test
    void missingOrBlankHmacIsRejected() throws Exception {
        assertThat(verifier.verify(sampleTransaction(), null)).isFalse();
        assertThat(verifier.verify(sampleTransaction(), "")).isFalse();
        assertThat(verifier.verify(sampleTransaction(), "   ")).isFalse();
    }

    @Test
    void unconfiguredSecretRejectsEverything() throws Exception {
        PaymobHmacVerifier unconfigured = new PaymobHmacVerifier("");
        assertThat(unconfigured.verify(sampleTransaction(), EXPECTED)).isFalse();
    }
}
