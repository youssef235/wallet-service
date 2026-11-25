package com.wallet.wallet.application;

import com.wallet.shared.application.TransactionRunner;
import com.wallet.shared.domain.NotFoundException;
import com.wallet.wallet.domain.LedgerEntryRepository;
import com.wallet.wallet.domain.Wallet;
import com.wallet.wallet.domain.WalletRepository;

public class TransactionHistoryUseCase {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRunner transactionRunner;

    public TransactionHistoryUseCase(WalletRepository walletRepository,
                                     LedgerEntryRepository ledgerEntryRepository,
                                     TransactionRunner transactionRunner) {
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRunner = transactionRunner;
    }

    public TransactionHistoryResult execute(Long userId, int offset, int limit) {
        return transactionRunner.inTransaction(() -> {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new NotFoundException("Wallet not found for user " + userId));
            var entries = ledgerEntryRepository.findByWalletId(wallet.id(), offset, limit).stream()
                    .map(TransactionEntryResult::from)
                    .toList();
            long total = ledgerEntryRepository.countByWalletId(wallet.id());
            return new TransactionHistoryResult(entries, total, offset, limit);
        });
    }
}
