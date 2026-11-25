package com.wallet.wallet.domain;

import java.util.List;

public interface LedgerEntryRepository {

    void saveAll(LedgerJournal journal);

    List<LedgerEntry> findByWalletId(Long walletId, int offset, int limit);

    long countByWalletId(Long walletId);

    /** Sum of all ledger amounts for one wallet account; the wallet's "real" balance. */
    long sumForWallet(Long walletId);
}
