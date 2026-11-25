package com.wallet.wallet.application;

import com.wallet.shared.application.TransactionRunner;
import com.wallet.shared.domain.NotFoundException;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;

/**
 * Returns the user's wallet. Every registered user has exactly one wallet,
 * opened at registration, so this read never writes.
 */
public class GetMyWalletUseCase {

    private final WalletRepository walletRepository;
    private final TransactionRunner transactionRunner;

    public GetMyWalletUseCase(WalletRepository walletRepository,
                              TransactionRunner transactionRunner) {
        this.walletRepository = walletRepository;
        this.transactionRunner = transactionRunner;
    }

    public WalletResult execute(Long userId) {
        return transactionRunner.inTransaction(() -> {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new NotFoundException(
                            "Wallet not found for user " + userId + " (wallets open at registration)"));
            return WalletResult.from(wallet);
        });
    }
}
