package com.wallet.payment.domain;

public enum TopUpStatus {
    PENDING,
    SUCCESS,
    NEEDS_REVIEW,
    REFUNDED,
    FAILED,
    EXPIRED
}
