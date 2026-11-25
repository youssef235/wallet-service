package com.wallet.payment.application;

public enum WebhookProcessingResult {
    CREDITED,
    PAYMENT_FAILED,
    ALREADY_PROCESSED,
    UNKNOWN_ORDER
}
