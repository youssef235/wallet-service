package com.wallet.wallet.presentation;

import com.wallet.wallet.application.TransferResult;
import java.time.Instant;

record TransferResponse(String idempotencyKey, long amountMinor, String currency,
                        long senderBalanceAfterMinor, String recipientEmail, Instant executedAt,
                        boolean replayed) {

    static TransferResponse from(TransferResult result) {
        return new TransferResponse(result.idempotencyKey(), result.amountMinor(), result.currency(),
                result.senderBalanceAfterMinor(), result.recipientEmail(), result.executedAt(), result.replayed());
    }
}
