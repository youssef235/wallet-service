package com.wallet.payment.domain;

import java.time.Instant;

/**
 * One reversal (refund/void) applied against a top-up. Several partial refunds
 * can hit the same top-up; each is recorded once, keyed by the reversal's own
 * Paymob transaction id (unique at the database).
 */
public record TopUpReversal(Long id, Long topupId, Long reversalTransactionId,
                            long amountMinor, Instant createdAt) {

    public static TopUpReversal record(Long topupId, Long reversalTransactionId,
                                       long amountMinor, Instant now) {
        if (amountMinor <= 0) {
            throw new InvalidTopUpException("Reversal amount must be positive");
        }
        return new TopUpReversal(null, topupId, reversalTransactionId, amountMinor, now);
    }
}
