package com.wallet.payment.application;

public record StartTopUpResult(Long topUpId, String merchantOrderId, long amountMinor,
                               String currency, String paymentUrl) {
}
