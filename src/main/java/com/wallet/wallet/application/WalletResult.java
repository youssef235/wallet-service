package com.wallet.wallet.application;

import com.wallet.wallet.domain.Wallet;

public record WalletResult(Long id, long balanceMinor, String currency) {

    public static WalletResult from(Wallet wallet) {
        return new WalletResult(wallet.id(), wallet.balanceMinor(), wallet.currency());
    }
}
