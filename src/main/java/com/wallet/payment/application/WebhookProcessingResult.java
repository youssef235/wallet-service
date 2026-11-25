package com.wallet.payment.application;

public enum WebhookProcessingResult {
    CREDITED,
    PAYMENT_FAILED,
    NEEDS_REVIEW,
    ALREADY_PROCESSED,
    UNKNOWN_ORDER
}
