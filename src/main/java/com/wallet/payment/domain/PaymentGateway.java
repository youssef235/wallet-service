package com.wallet.payment.domain;

/**
 * The external card-payment provider (Paymob in production, a local stub in dev).
 * The wallet itself never talks to Paymob directly: it only sees this interface.
 */
public interface PaymentGateway {

    PaymentIntent createPaymentIntent(Long topUpId, String merchantOrderId, long amountMinor,
                                      String currency, String customerEmail, String customerFullName);

    record PaymentIntent(Long providerOrderId, String paymentUrl) {
    }
}
