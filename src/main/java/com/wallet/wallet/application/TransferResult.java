package com.wallet.wallet.application;

import java.time.Instant;

public record TransferResult(String idempotencyKey, long amountMinor, String currency,
                             long senderBalanceAfterMinor, String recipientEmail, Instant executedAt,
                             boolean replayed) {
}
