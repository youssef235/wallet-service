package com.wallet.wallet.domain;

/**
 * A ledger account is either a user's wallet or an internal system account.
 * Every journal entry moves money between exactly these accounts, so the books always balance.
 */
public record LedgerAccount(String value) {

    /** Cleared but not yet settled card/wallet payments coming from Paymob. */
    public static final LedgerAccount PAYMOB_CLEARING = new LedgerAccount("SYSTEM:PAYMOB_CLEARING");

    public static LedgerAccount ofWallet(Long walletId) {
        return new LedgerAccount("WALLET:" + walletId);
    }

    public boolean isWalletAccount() {
        return value.startsWith("WALLET:");
    }

    public Long walletId() {
        if (!isWalletAccount()) {
            throw new IllegalStateException("Not a wallet account: " + value);
        }
        return Long.valueOf(value.substring("WALLET:".length()));
    }
}
