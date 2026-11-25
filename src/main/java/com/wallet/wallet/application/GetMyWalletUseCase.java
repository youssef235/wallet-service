package com.wallet.wallet.application;

import com.wallet.shared.application.TransactionRunner;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;
import java.time.Clock;
import java.time.Instant;

/**
 * Returns the user's wallet, opening it on first access. Every registered user
 * has exactly one wallet; it is created lazily the first time it is needed.
 */
public class GetMyWalletUseCase {

    private final WalletRepository walletRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;
    private final String defaultCurrency;

    public GetMyWalletUseCase(WalletRepository walletRepository,
                              TransactionRunner transactionRunner,
                              Clock clock,
                              String defaultCurrency) {
        this.walletRepository = walletRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
        this.defaultCurrency = defaultCurrency;
    }

    public WalletResult execute(Long userId) {
        return transactionRunner.inTransaction(() -> {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseGet(() -> walletRepository.save(
                            Wallet.openFor(userId, defaultCurrency, Instant.now(clock))));
            return WalletResult.from(wallet);
        });
    }
}
