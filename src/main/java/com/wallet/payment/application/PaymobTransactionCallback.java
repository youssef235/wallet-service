package com.wallet.payment.application;

/**
 * The fields we use from a Paymob "transaction processed" webhook, already
 * HMAC-verified by the caller. A payment only counts as settled when
 * success is true and none of pending/errorOccured/isVoided/isRefunded are.
 */
public record PaymobTransactionCallback(Long transactionId,
                                        String merchantOrderId,
                                        long amountCents,
                                        String currency,
                                        boolean success,
                                        boolean pending,
                                        boolean errorOccured,
                                        boolean isVoided,
                                        boolean isRefunded) {

    boolean settled() {
        return success && !pending && !errorOccured && !isVoided && !isRefunded;
    }
}
