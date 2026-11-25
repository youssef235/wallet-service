package com.wallet.wallet.presentation;

import com.wallet.wallet.application.WalletResult;

record WalletResponse(Long id, long balanceMinor, String currency, String balanceFormatted) {

    static WalletResponse from(WalletResult result) {
        return new WalletResponse(result.id(), result.balanceMinor(), result.currency(),
                format(result.balanceMinor(), result.currency()));
    }

    private static String format(long amountMinor, String currency) {
        return String.format("%.2f %s", amountMinor / 100.0, currency);
    }
}
