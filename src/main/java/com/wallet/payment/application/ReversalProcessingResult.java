package com.wallet.payment.application;

public enum ReversalProcessingResult {
    /** The wallet was debited and the top-up marked REFUNDED. */
    REVERSED,
    /** This reversal transaction was applied before (Paymob retries callbacks). */
    ALREADY_PROCESSED,
    /** The referenced top-up is not in a reversible state; nothing changed. */
    IGNORED,
    /** No top-up exists for the original Paymob transaction id. */
    UNKNOWN_TRANSACTION
}
