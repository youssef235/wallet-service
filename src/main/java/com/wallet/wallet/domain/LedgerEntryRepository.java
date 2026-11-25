package com.wallet.wallet.domain;

import java.util.List;

public interface LedgerEntryRepository {

    void saveAll(LedgerJournal journal);

    /** Keyset pagination: newest first, entries with id < beforeId (null = first page). */
    List<LedgerEntry> findByWalletId(Long walletId, Long beforeId, int limit);

    /** Sum of all ledger amounts for one wallet account; the wallet's "real" balance. */
    long sumForWallet(Long walletId);
}
