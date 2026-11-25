package com.wallet.payment.presentation;

import com.wallet.payment.application.TopUpResult;
import java.time.Instant;

record TopUpResponse(Long id, long amountMinor, String currency, String status,
                     String merchantOrderId, Instant createdAt) {

    static TopUpResponse from(TopUpResult result) {
        return new TopUpResponse(result.id(), result.amountMinor(), result.currency(), result.status(),
                result.merchantOrderId(), result.createdAt());
    }
}
