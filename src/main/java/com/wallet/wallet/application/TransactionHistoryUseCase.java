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

    public TransactionHistoryResult execute(Long userId, Long beforeId, int limit) {
        return transactionRunner.inTransaction(() -> {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new NotFoundException("Wallet not found for user " + userId));
            var page = ledgerEntryRepository.findByWalletId(wallet.id(), beforeId, limit);
            var entries = page.stream().map(TransactionEntryResult::from).toList();
            // A full page means there may be more; the cursor is the oldest id returned.
            Long nextBeforeId = page.size() == limit ? page.get(limit - 1).id() : null;
            return new TransactionHistoryResult(entries, nextBeforeId, limit);
        });
    }
}
