package com.wallet.wallet.domain;

import java.time.Instant;

/**
 * Record of a P2P transfer. The idempotency key is unique, so a retried request
 * returns the original transfer instead of moving money twice.
 */
public record Transfer(Long id, String idempotencyKey, Long fromWalletId, Long toWalletId,
                       long amountMinor, String journalId, Instant createdAt) {

    public static Transfer of(String idempotencyKey, Long fromWalletId, Long toWalletId,
                              long amountMinor, String journalId, Instant now) {
        return new Transfer(null, idempotencyKey, fromWalletId, toWalletId, amountMinor, journalId, now);
    }
}
