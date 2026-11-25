package com.wallet.payment.application;

import com.wallet.payment.domain.TopUp;
import java.time.Instant;

public record TopUpResult(Long id, long amountMinor, String currency, String status,
                          String merchantOrderId, Instant createdAt) {

    public static TopUpResult from(TopUp topUp) {
        return new TopUpResult(topUp.id(), topUp.amountMinor(), topUp.currency(),
                topUp.status().name(), topUp.merchantOrderId(), topUp.createdAt());
    }
}
