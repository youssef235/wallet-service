package com.wallet.payment.application;

/**
 * The fields we use from a Paymob "transaction processed" webhook, already
 * HMAC-verified by the caller.
 */
public record PaymobTransactionCallback(Long transactionId,
                                        String merchantOrderId,
                                        long amountCents,
                                        boolean success) {
}
