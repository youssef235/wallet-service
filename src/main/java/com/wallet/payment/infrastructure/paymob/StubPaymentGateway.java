package com.wallet.payment.infrastructure.paymob;

import com.wallet.payment.domain.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local-development stand-in for Paymob, active only when app.paymob.enabled=false.
 * It returns a fake payment page URL so the API flow can be tried without real
 * credentials. The webhook endpoint and all money logic stay fully real; only the
 * hosted payment page is stubbed.
 */
@Component
@ConditionalOnProperty(name = "app.paymob.enabled", havingValue = "false", matchIfMissing = true)
class StubPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StubPaymentGateway.class);

    @Override
    public PaymentIntent createPaymentIntent(Long topUpId, String merchantOrderId, long amountMinor,
                                             String currency, String customerEmail, String customerFullName) {
        log.info("Stub payment gateway: pretending to create Paymob payment for top-up {} ({} {})",
                topUpId, amountMinor, currency);
        long fakeOrderId = 10_000_000L + topUpId;
        String fakeUrl = "http://localhost:8081/stub-checkout/" + merchantOrderId;
        return new PaymentIntent(fakeOrderId, fakeUrl);
    }
}
